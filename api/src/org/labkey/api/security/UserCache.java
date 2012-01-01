package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: 12/30/11
 * Time: 9:02 AM
 */

// This cache keeps various collections of users in memory for fast access. The cache holds a single element containing
// various user collections; all collections are populated via a single bulk query. This scales better than adding and
// invalidating individual user objects since many common operations require the full list of users (or active users).
//
// Caching a single object may seem a bit silly, but it provides mem tracker integration, statistics gathering, etc. for free.
public class UserCache
{
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final BlockingStringKeyCache<UserCollections> CACHE = CacheManager.getBlockingStringKeyCache(1, CacheManager.DAY, "Users", new UserCollectionsLoader());
    private static final String KEY = "USER_COLLECTIONS";

    private UserCache()
    {
    }


    public static @Nullable User getUser(int userId)
    {
        return getUserMaps().getAllUsers().get(userId);
    }


    // Users returned in email order... maybe they should be sorted by display name?
    public static @NotNull Collection<User> getActiveUsers()
    {
        return getUserMaps().getActiveUsers();
    }


    // Emails are returned sorted
    public static @NotNull Collection<String> getActiveUserEmails()
    {
        return getUserMaps().getActiveEmails();
    }


    public static int getActiveUserCount()
    {
        return getActiveUsers().size();
    }


    // All collections are invalidated for any user change. This seems like the right balance between perf, complexity,
    // and synchronization... but we'll keep requiring the user ID for now just in case.
    @SuppressWarnings({"UnusedParameters"})
    static void remove(int userId)
    {
        assert userId != 0;
        CACHE.remove(KEY);
    }


    private static UserCollections getUserMaps()
    {
        return CACHE.get(KEY);
    }


    private static class UserCollections
    {
        private final Map<Integer, User> _allUsers;
        private final List<User> _activeUsers;
        private final List<String> _activeEmails;

        private UserCollections(Map<Integer, User> allUsers, List<User> activeUsers, List<String> activeEmails)
        {
            _allUsers = Collections.unmodifiableMap(allUsers);
            _activeUsers = Collections.unmodifiableList(activeUsers);
            _activeEmails = Collections.unmodifiableList(activeEmails);
        }

        public Map<Integer, User> getAllUsers()
        {
            return _allUsers;
        }

        public List<User> getActiveUsers()
        {
            return _activeUsers;
        }

        public List<String> getActiveEmails()
        {
            return _activeEmails;
        }
    }


    private static class UserCollectionsLoader implements CacheLoader<String, UserCollections>
    {
        @Override
        public UserCollections load(String key, @Nullable Object argument)
        {
            Collection<User> allUsers = new TableSelector(CORE.getTableInfoUsers(), null, new Sort("Email")).getCollection(User.class);

            Map<Integer, User> allUsersMap = new HashMap<Integer, User>((int)(1.3 * allUsers.size()));
            List<User> activeUsers = new LinkedList<User>();
            List<String> activeEmails = new LinkedList<String>();

            for (User user : allUsers)
            {
                Integer userId = user.getUserId();
                allUsersMap.put(userId, user);

                if (user.isActive())
                {
                    activeUsers.add(user);
                    activeEmails.add(user.getEmail()); // Sorted, since user's are sorted by email address
                }
            }

            return new UserCollections(allUsersMap, activeUsers, activeEmails);
        }
    }
}
