package org.labkey.api.security;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.SimpleFilter.NotClause;
import org.labkey.api.data.SimpleFilter.SQLClause;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.TestContext;

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
     * @param expirationSeconds Number of seconds until expiration. -1 means no expiration.
     * @return An API key that expires after the specified number of seconds
     */
    public @NotNull String createKey(@NotNull User user, int expirationSeconds)
    {
//        if (user.isGuest())
//            throw new Exception();

        String apiKey = "apikey|" + GUID.makeHash();

        Map<String, Object> map = new HashMap<>();
        map.put("Crypt", crypt(apiKey));

        if (-1 != expirationSeconds)
        {
            LocalDateTime ldt = LocalDateTime.now().plusSeconds(expirationSeconds);
            Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
            map.put("Expiration", Date.from(instant));
        }

        Table.insert(user, CoreSchema.getInstance().getTableAPIKeys(), map);

        return apiKey;
    }

    public @Nullable User authenticateFromApiKey(@NotNull String apiKey)
    {
        SimpleFilter filter = new SimpleFilter(getStillValidClause());
        Integer userId = new TableSelector(CoreSchema.getInstance().getTableAPIKeys(), Collections.singleton("CreatedBy"), filter, null).getObject(crypt(apiKey), Integer.class);

        return null != userId ? UserManager.getUser(userId) : null;
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
        public void test() throws InterruptedException
        {
            User user = TestContext.get().getUser();
            String oneSecondKey = ApiKeyManager.get().createKey(user, 1);
            String noExpireKey = ApiKeyManager.get().createKey(user, -1);

            assertEquals(user, ApiKeyManager.get().authenticateFromApiKey(oneSecondKey));
            Thread.sleep(1100);
            assertNull("API key should have expired after one second", ApiKeyManager.get().authenticateFromApiKey(oneSecondKey));

            assertEquals(user, ApiKeyManager.get().authenticateFromApiKey(noExpireKey));
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
            Table.delete(CoreSchema.getInstance().getTableAPIKeys(), new SimpleFilter(new NotClause(getStillValidClause())));
        }

        @Override
        public boolean canDisable()
        {
            return false;
        }
    }
}
