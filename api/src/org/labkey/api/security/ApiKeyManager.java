/*
 * Copyright (c) 2017-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.DbScope.TransactionKind;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.NotClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.UserManager.SessionHandler;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.security.roles.Role;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;

public class ApiKeyManager
{
    private static final Logger LOG = LogHelper.getLogger(ApiKeyManager.class, "API key bookkeeping");
    private static final ApiKeyManager INSTANCE = new ApiKeyManager();
    private static final TransactionKind TRANSACTION_KIND = () -> "APIKEY";

    public static final String API_KEY_ROW_ID = "apiKeyRowID";

    public static ApiKeyManager get()
    {
        return INSTANCE;
    }

    private ApiKeyManager()
    {
    }

    /**
     * Create an API key associated with a user and persist it in the database.
     * @param user User to be associated with the new API key.
     * @return An API key that expires after the admin-configured duration
     */
    public @NotNull String createKey(@NotNull User user, @Nullable String description, @Nullable Class<? extends Role> restrictionRole)
    {
        return createKey(user, AppProps.getInstance().getApiKeyExpirationSeconds(), description, restrictionRole);
    }

    /**
     * Create an API key associated with a user and persist it in the database.
     * @param user User to be associated with the new API key.
     * @param expirationSeconds Number of seconds until expiration. -1 means no expiration.
     * @return An API key that expires after the specified number of seconds
     */
    public @NotNull String createKey(@NotNull User user, int expirationSeconds, @Nullable String description)
    {
        return createKey(user, expirationSeconds, description, null);
    }

    /**
     * Create an API key associated with a user and persist it in the database.
     * @param user User to be associated with the new API key.
     * @param expirationSeconds Number of seconds until expiration. -1 means no expiration.
     * @param restrictionRole Role class that limits this API key's permissions. null means no restrictions.
     * @return An API key that expires after the specified number of seconds
     */
    public @NotNull String createKey(@NotNull User user, int expirationSeconds, @Nullable String description, @Nullable Class<? extends Role> restrictionRole)
    {
        if (user.isGuest())
            throw new IllegalStateException("Can't create an API key for a guest");

        // Disallow API key creation while impersonating, #34580
        if (user.isImpersonated())
            throw new UnauthorizedException("Can't create an API key while impersonating");

        Map<String, Object> map = new HashMap<>();

        String apiKey = GUID.makeLongHash();
        map.put("Crypt", crypt(apiKey));

        if (-1 != expirationSeconds)
        {
            LocalDateTime ldt = LocalDateTime.now().plusSeconds(expirationSeconds);
            Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
            map.put("Expiration", Date.from(instant));
        }

        if (description != null)
            map.put("Description", StringUtils.abbreviate(description.trim(), 256));

        if (restrictionRole != null)
            map.put("RestrictionRole", restrictionRole.getName());

        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            Table.insert(user, CoreSchema.getInstance().getTableAPIKeys(), map);
            t.commit();
        }

        return apiKey;
    }

    // Delete a single API key and clear any active sessions that were established using it
    public void deleteKey(String apiKey)
    {
        deleteKeys(new SimpleFilter(FieldKey.fromParts("Crypt"), crypt(apiKey)));
    }

    // Delete all API keys that match the filter and clear all active sessions that were established using them
    public void deleteKeys(Filter filter)
    {
        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            new TableSelector(CoreSchema.getInstance().getTableAPIKeys(), filter, null)
                .forEach(ApiKeyAuthentication.class, auth -> deleteKey(auth.getUser(), auth.rowId()));
            t.commit();
        }
    }

    // Delete a single API key and clear all active sessions that were established using it
    private void deleteKey(User user, int rowId)
    {
        Table.delete(CoreSchema.getInstance().getTableAPIKeys(), rowId);
        UserManager.handleSessionsForUser(user, new SessionHandler()
        {
            @Override
            public boolean handleSession(HttpSession session)
            {
                LOG.debug("Checking if session {} used API key {}", session.getId(), rowId);
                Map<String, Object> map = AuthenticationManager.getAuthenticationProperties(session);
                Integer apiKeyRowId = asInteger(map.get(API_KEY_ROW_ID));

                if (Objects.equals(rowId, apiKeyRowId))
                {
                    LOG.debug("Attempting to invalidate session {} which used API key {}", session.getId(), rowId);
                    session.invalidate();
                    return true;
                }

                return false;
            }

            @Override
            public void complete(int count)
            {
                LOG.debug("Invalidated {} for {}.", StringUtilsLabKey.pluralize(count, "API key session"), user.getEmail());
            }
        });
    }

    public void updateLastUsed(String apikey)
    {
        DbScope scope = CoreSchema.getInstance().getScope();

        try (Transaction t = scope.beginTransaction(TRANSACTION_KIND))
        {
            SQLFragment sql = new SQLFragment("UPDATE ")
                .append(CoreSchema.getInstance().getTableAPIKeys())
                .append(" SET LastUsed = ? WHERE Crypt = ?")
                .add(new Date())
                .add(crypt(apikey));
            new SqlExecutor(scope).execute(sql);
            t.commit();
        }
    }

    public record ApiKeyAuthentication(int createdBy, int rowId, @Nullable Class<? extends Role> restrictionRole)
    {
        public User getUser()
        {
            return UserManager.getUser(createdBy());
        }
    }

    /**
     * Returns a record containing the User associated with the supplied API key and the API key's rowID, if API key is
     * valid. User could be inactive.
     * @param apiKey The API key to validate
     * @return An ApiKeyAuthentication associated with the API key or null if API key is invalid
     */
    public @Nullable ApiKeyAuthentication authenticateFromApiKey(@NotNull String apiKey)
    {
        SimpleFilter filter = new SimpleFilter(getStillValidClause());
        filter.addCondition(FieldKey.fromParts("Crypt"), crypt(apiKey));

        ApiKeyAuthentication ret;

        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            ret = new TableSelector(CoreSchema.getInstance().getTableAPIKeys(), Set.of("CreatedBy", "RowId"), filter, null).getObject(ApiKeyAuthentication.class);
            t.commit();
        }

        return ret;
    }

    private static final String API_KEY_SCOPE = "ApiKey";

    private static final class ApiKeyStartupProperty implements StartupProperty
    {
        @Override
        public String getPropertyName()
        {
            return "<user email address>";
        }

        @Override
        public String getDescription()
        {
            return "Assigns the specified API key to the specified user. User must already exist.";
        }
    }

    public void handleStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(new LenientStartupPropertyHandler<>(API_KEY_SCOPE, new ApiKeyStartupProperty())
        {
            @Override
            public void handle(Collection<StartupPropertyEntry> entries)
            {
                entries.forEach(prop -> {
                    try
                    {
                        User user = UserManager.getUser(new ValidEmail(prop.getName()));

                        if (null == user)
                            throw new ConfigurationException("Unrecognized user specified in ApiKey startup property: " + prop.getName());

                        String apiKey = prop.getValue();

                        createKey(user, -1, apiKey);
                    }
                    catch (InvalidEmailException e)
                    {
                        throw new ConfigurationException("Invalid email address specified in ApiKey startup property: " + prop.getName(), e);
                    }
                });
            }
        });
    }

    private static @NotNull String crypt(@NotNull String apiKey)
    {
        return Crypt.SHA256.digestWithPrefix(apiKey);
    }

    private static FilterClause getStillValidClause()
    {
        return new SQLClause(new SQLFragment("Expiration IS NULL OR Expiration > ?", new Date()), FieldKey.fromParts("Expiration"));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testCreateAndExpire() throws InterruptedException
        {
            User user = TestContext.get().getUser();
            String oneSecondKey = ApiKeyManager.get().createKey(user, 1, "Created by ApiKeyManager.TestCase");
            String noExpireKey = ApiKeyManager.get().createKey(user, -1, "Created by ApiKeyManager.TestCase");

            ApiKeyAuthentication auth = ApiKeyManager.get().authenticateFromApiKey(oneSecondKey);
            assertNotNull(auth);
            assertEquals(user, auth.getUser());
            Thread.sleep(1100);
            assertNull("API key should have expired after one second", ApiKeyManager.get().authenticateFromApiKey(oneSecondKey));

            auth = ApiKeyManager.get().authenticateFromApiKey(noExpireKey);
            assertNotNull(auth);
            assertEquals(user, auth.getUser());

            ApiKeyManager.get().deleteKey(oneSecondKey);
            ApiKeyManager.get().deleteKey(noExpireKey);
            assertNull(ApiKeyManager.get().authenticateFromApiKey(noExpireKey));
        }

        @Test(expected = IllegalStateException.class)
        public void testGuest()
        {
            // Should throw
            ApiKeyManager.get().createKey(User.guest, -1, "Created by ApiKeyManager.TestCase");
        }

        @Test
        public void testTransaction()
        {
            User user = TestContext.get().getUser();

            String apikey;

            try (Transaction ignored = DbScope.getLabKeyScope().beginTransaction())
            {
                apikey = ApiKeyManager.get().createKey(user, 10, "Created by ApiKeyManager.TestCase");
                ApiKeyAuthentication auth = ApiKeyManager.get().authenticateFromApiKey(apikey);
                assertNotNull(auth);
                assertEquals(user, auth.getUser());
            }

            ApiKeyAuthentication auth = ApiKeyManager.get().authenticateFromApiKey(apikey);
            assertNotNull(auth);
            assertEquals("API key should be valid even after rollback", user, auth.getUser());

            ApiKeyManager.get().deleteKey(apikey);
            assertNull(ApiKeyManager.get().authenticateFromApiKey(apikey));
        }
    }

    public static class ApiKeyMaintenanceTask implements MaintenanceTask
    {
        @Override
        public String getDescription()
        {
            return "Purge expired API keys";
        }

        @Override
        public String getName()
        {
            return "PurgeExpiredApiKeys";
        }

        @Override
        public void run(Logger log)
        {
            // Delete all rows that are no longer valid (expired)
            ApiKeyManager.get().deleteKeys(new SimpleFilter(new NotClause(getStillValidClause())));
        }

        @Override
        public boolean canDisable()
        {
            return false;
        }
    }
}
