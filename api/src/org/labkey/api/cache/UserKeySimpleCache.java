package org.labkey.api.cache;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.TestContext;

import java.util.Set;
import java.util.stream.Collectors;

// Wrapped users (e.g., LimitedUser) share the entry of the user they wrap
public class UserKeySimpleCache<V> extends SimpleKeyMappingCache<User, Integer, V>
{
    public UserKeySimpleCache(@NotNull SimpleCache<Integer, V> delegate)
    {
        super(delegate, User::getUserId, UserManager::getUser);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testUserKeys()
        {
            User user = TestContext.get().getUser();

            try (Cache<User, String> cache = CacheManager.getTemporaryCache(User.class, 10, CacheManager.UNLIMITED, "UserKeySimpleCache test", null))
            {
                cache.put(user, "user");
                cache.put(User.guest, "guest");
                assertEquals(Set.of(user.getUserId(), User.guest.getUserId()), cache.getKeys().map(User::getUserId).collect(Collectors.toSet()));

                User limited = new LimitedUser(user, ReaderRole.class);
                assertEquals("user", cache.get(limited));
                cache.remove(limited);
                assertNull(cache.get(user));
                assertEquals("guest", cache.get(User.guest));
            }
        }
    }
}
