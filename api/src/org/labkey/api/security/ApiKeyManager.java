/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.NotClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ApiKeyManager
{
    private final static ApiKeyManager INSTANCE = new ApiKeyManager();
    private final static TransactionKind TRANSACTION_KIND = () -> "APIKEY";

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
    public @NotNull String createKey(@NotNull User user)
    {
        return createKey(user, AppProps.getInstance().getApiKeyExpirationSeconds());
    }

    /**
     * Create an API key associated with a user and persist it in the database.
     * @param user User to be associated with the new API key.
     * @param expirationSeconds Number of seconds until expiration. -1 means no expiration.
     * @return An API key that expires after the specified number of seconds
     */
    public @NotNull String createKey(@NotNull User user, int expirationSeconds)
    {
        return createKey(user, expirationSeconds, "apikey|" + GUID.makeHash());
    }

    private @NotNull String createKey(@NotNull User user, int expirationSeconds, String apiKey)
    {
        if (user.isGuest())
            throw new IllegalStateException("Can't create an API key for a guest");

        // Disallow API key creation while impersonating, #34580
        if (user.isImpersonated())
            throw new UnauthorizedException("Can't create an API key while impersonating");

        Map<String, Object> map = new HashMap<>();
        map.put("Crypt", crypt(apiKey));

        if (-1 != expirationSeconds)
        {
            LocalDateTime ldt = LocalDateTime.now().plusSeconds(expirationSeconds);
            Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
            map.put("Expiration", Date.from(instant));
        }

        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            Table.insert(user, CoreSchema.getInstance().getTableAPIKeys(), map);
            t.commit();
        }

        return apiKey;
    }

    public void deleteKey(String apikey)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Crypt"), crypt(apikey));

        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            Table.delete(CoreSchema.getInstance().getTableAPIKeys(), filter);
            t.commit();
        }
    }

    public @Nullable User authenticateFromApiKey(@NotNull String apiKey)
    {
        SimpleFilter filter = new SimpleFilter(getStillValidClause());
        filter.addCondition(FieldKey.fromParts("Crypt"), crypt(apiKey));

        Integer userId;

        try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
        {
            userId = new TableSelector(CoreSchema.getInstance().getTableAPIKeys(), Collections.singleton("CreatedBy"), filter, null).getObject(Integer.class);
            t.commit();
        }

        User user = null != userId ? UserManager.getUser(userId) : null;
        if (user != null && !user.isActive())
            return null;
        return user;
    }

    private static final String API_KEY_SCOPE = "ApiKey";

    public void handleStartupProperties()
    {
        ModuleLoader.getInstance().getConfigProperties(API_KEY_SCOPE).forEach(prop->{
            try
            {
                User user = UserManager.getUser(new ValidEmail(prop.getName()));

                if (null == user)
                    throw new ConfigurationException("Unrecognized user specified in ApiKey startup property: " + prop.getName());

                String apiKey = prop.getValue();

                if (!StringUtils.startsWith(apiKey, "apikey|"))
                    throw new ConfigurationException("Invalid API key specified in ApiKey startup property; API keys must start with \"apikey|\": " + apiKey);

                createKey(user, -1, apiKey);
            }
            catch (InvalidEmailException e)
            {
                throw new ConfigurationException("Invalid email address specified in ApiKey startup property: " + prop.getName(), e);
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
            String oneSecondKey = ApiKeyManager.get().createKey(user, 1);
            String noExpireKey = ApiKeyManager.get().createKey(user, -1);

            assertEquals(user, ApiKeyManager.get().authenticateFromApiKey(oneSecondKey));
            Thread.sleep(1100);
            assertNull("API key should have expired after one second", ApiKeyManager.get().authenticateFromApiKey(oneSecondKey));

            assertEquals(user, ApiKeyManager.get().authenticateFromApiKey(noExpireKey));

            ApiKeyManager.get().deleteKey(oneSecondKey);
            ApiKeyManager.get().deleteKey(noExpireKey);
            assertNull(ApiKeyManager.get().authenticateFromApiKey(noExpireKey));
        }

        @Test(expected = IllegalStateException.class)
        public void testGuest()
        {
            // Should throw
            ApiKeyManager.get().createKey(User.guest, -1);
        }

        @Test
        public void testTransaction()
        {
            User user = TestContext.get().getUser();

            String apikey;

            try (Transaction ignored = DbScope.getLabKeyScope().beginTransaction())
            {
                apikey = ApiKeyManager.get().createKey(user, 10);
                assertEquals(user, ApiKeyManager.get().authenticateFromApiKey(apikey));
            }

            assertEquals("API key should be valid even after rollback", user, ApiKeyManager.get().authenticateFromApiKey(apikey));

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
            try (Transaction t = CoreSchema.getInstance().getScope().beginTransaction(TRANSACTION_KIND))
            {
                // Delete all rows that are no longer valid (expired)
                Table.delete(CoreSchema.getInstance().getTableAPIKeys(), new SimpleFilter(new NotClause(getStillValidClause())));
                t.commit();
            }
        }

        @Override
        public boolean canDisable()
        {
            return false;
        }
    }
}
