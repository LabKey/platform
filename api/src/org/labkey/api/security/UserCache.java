/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.Tracking;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.HttpView;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This cache keeps various collections of users in memory for fast access. The cache holds a single element containing
 * various user collections; all collections are populated via a single bulk query. This scales better than adding and
 * invalidating individual user objects since many common operations require the full list of users (or active users).
 *
 * Caching a single object may seem a bit silly, but it provides mem tracker integration, statistics gathering, etc. for free.
 * User: adam
 * Date: 12/30/11
 */
class UserCache
{
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final String KEY = "USER_COLLECTIONS";

    private static final StringKeyCache<UserCollections> CACHE = new DatabaseCache<UserCollections>(CORE.getSchema().getScope(), 2, CacheManager.DAY, "User Collections") {
        @Override
        protected StringKeyCache<UserCollections> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
        {
            StringKeyCache<Wrapper<UserCollections>> shared = CacheManager.getStringKeyCache(maxSize, defaultTimeToLive, debugName);
            return new BlockingStringKeyCache<>(shared, new UserCollectionsLoader());
        }

        @Override
        protected StringKeyCache<UserCollections> createTemporaryCache(StringKeyCache<UserCollections> sharedCache)
        {
            Tracking tracking = sharedCache.getTrackingCache();
            StringKeyCache<Wrapper<UserCollections>> temp = CacheManager.getTemporaryCache(tracking.getLimit(), tracking.getDefaultExpires(), "Transaction cache: User Collections", tracking.getStats());
            return new BlockingStringKeyCache<>(temp, new UserCollectionsLoader());
        }
    };

    private UserCache()
    {
    }


    // Return a new copy of the User with this userId, or null if user doesn't exist
    static @Nullable User getUser(int userId)
    {
        User user = getUserCollections().getUserIdMap().get(userId);

        // these should really be readonly
        return null != user ? user.cloneUser() : null;
    }


    // Return a new copy of the User with this email address, or null if user doesn't exist
    static @Nullable User getUser(ValidEmail email)
    {
        User user = getUserCollections().getEmailMap().get(email);

        // these should really be readonly
        return null != user ? user.cloneUser() : null;
    }


    // Return a new copy of the User with this display name, or null if user doesn't exist
    static @Nullable User getUser(String displayName)
    {
        User user = getUserCollections().getDisplayNameMap().get(displayName);

        // these should really be readonly
        return null != user ? user.cloneUser() : null;
    }


    // Returns a deep copy of the active users list, allowing callers to interrogate user permissions without affecting
    // cached users. Collection is ordered by email... maybe it should be ordered by display name?
    static @NotNull Collection<User> getActiveUsers()
    {
        List<User> activeUsers = getUserCollections().getActiveUsers();

        return activeUsers
            .stream()
            .map(User::cloneUser)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    // Returns a deep copy of the users list including deactivated accounts, allowing callers to interrogate user permissions
    // without affecting cached users. Collection is ordered randomly... maybe it should be ordered by display name?
    static @NotNull Collection<User> getActiveAndInactiveUsers()
    {
        Collection<User> users = getUserCollections().getUserIdMap().values();

        return users
                .stream()
                .map(User::cloneUser)
                .collect(Collectors.toList());
    }

    static @NotNull List<Integer> getUserIds()
    {
        return new LinkedList<>(getUserCollections().getUserIdMap().keySet());
    }

    static @NotNull Map<ValidEmail, User> getUserEmailMap()
    {
        return Collections.unmodifiableMap(getUserCollections().getEmailMap());
    }

    static int getActiveUserCount()
    {
        return getUserCollections().getActiveUserCount();
    }


    /**
     * All collections are invalidated for any user change. This seems like the right balance between perf, complexity,
     * and synchronization
     */
    static void clear()
    {
        CACHE.remove(KEY);
    }


    private static UserCollections getUserCollections()
    {
        return CACHE.get(KEY);
    }


    private static class UserCollections
    {
        private final Map<Integer, User> _userIdMap;
        private final Map<ValidEmail, User> _emailMap;
        private final Map<String, User> _displayNameMap;
        private final List<User> _activeUsers;

        private UserCollections(Map<Integer, User> userIdMap, Map<ValidEmail, User> emailMap, Map<String, User> displayNameMap, List<User> activeUsers)
        {
            _userIdMap = Collections.unmodifiableMap(userIdMap);
            _emailMap = Collections.unmodifiableMap(emailMap);
            _displayNameMap = displayNameMap;
            _activeUsers = Collections.unmodifiableList(activeUsers);
        }

        private Map<Integer, User> getUserIdMap()
        {
            return _userIdMap;
        }

        private Map<ValidEmail, User> getEmailMap()
        {
            return _emailMap;
        }

        private Map<String, User> getDisplayNameMap()
        {
            return _displayNameMap;
        }

        private List<User> getActiveUsers()
        {
            return _activeUsers;
        }

        private int getActiveUserCount()
        {
            return _activeUsers.size();
        }
    }


    private static class UserCollectionsLoader implements CacheLoader<String, UserCollections>
    {
        @Override
        public UserCollections load(String key, @Nullable Object argument)
        {
            Collection<User> allUsers = new TableSelector(CORE.getTableInfoUsers(), null, new Sort("Email")).getCollection(User.class);

            Map<Integer, User> userIdMap = new HashMap<>((int)(1.3 * allUsers.size()));
            Map<ValidEmail, User> emailMap = new HashMap<>((int)(1.3 * allUsers.size()));
            Map<String, User> displayNameMap = new HashMap<>((int)(1.3 * allUsers.size()));
            List<User> activeUsers = new LinkedList<>();

            // We're using the same User object in multiple lists... UserCache must clone all users it returns to prevent
            // concurrency issues (e.g., a caller might indirectly modify a cached User's group list, leading to stale credentials)
            for (User user : allUsers)
            {
                Integer userId = user.getUserId();
                userIdMap.put(userId, user);
                displayNameMap.put(user.getFriendlyName(), user);

                try
                {
                    emailMap.put(new ValidEmail(user.getEmail()), user);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    // We have stored a bad email address in the database (see #12276 for one example of how this can happen);
                    // skip this email address but log the problem back to LabKey.
                    ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), e);
                }

                if (user.isActive())
                    activeUsers.add(user);
            }

            return new UserCollections(userIdMap, emailMap, displayNameMap, activeUsers);
        }
    }
}
