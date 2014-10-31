/*
 * Copyright (c) 2012-2014 LabKey Corporation
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


    // Returns a deep copy of the active users list, allowing callers to interogate user permissions without affecting
    // cached users. Collection is ordered by email... maybe it should be ordered by display name?
    static @NotNull Collection<User> getActiveUsers()
    {
        List<User> activeUsers = getUserCollections().getActiveUsers();
        List<User> copy = new LinkedList<>();

        for (User user : activeUsers)
            copy.add(user.cloneUser());

        return copy;
    }


    // Emails are returned sorted
    static @NotNull List<String> getActiveUserEmails()
    {
        return new LinkedList<>(getUserCollections().getActiveEmails());
    }

    static @NotNull List<Integer> getUserIds()
    {
        return new LinkedList<>(getUserCollections().getUserIdMap().keySet());
    }

    static int getActiveUserCount()
    {
        return getActiveUsers().size();
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
        private final List<User> _activeUsers;
        private final List<String> _activeEmails;

        private UserCollections(Map<Integer, User> userIdMap, Map<ValidEmail, User> emailMap, List<User> activeUsers, List<String> activeEmails)
        {
            _userIdMap = Collections.unmodifiableMap(userIdMap);
            _emailMap = Collections.unmodifiableMap(emailMap);
            _activeUsers = Collections.unmodifiableList(activeUsers);
            _activeEmails = Collections.unmodifiableList(activeEmails);
        }

        private Map<Integer, User> getUserIdMap()
        {
            return _userIdMap;
        }

        public Map<ValidEmail, User> getEmailMap()
        {
            return _emailMap;
        }

        private List<User> getActiveUsers()
        {
            return _activeUsers;
        }

        private List<String> getActiveEmails()
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

            Map<Integer, User> userIdMap = new HashMap<>((int)(1.3 * allUsers.size()));
            Map<ValidEmail, User> emailMap = new HashMap<>((int)(1.3 * allUsers.size()));
            List<User> activeUsers = new LinkedList<>();
            List<String> activeEmails = new LinkedList<>();

            // We're using the same User object in multiple lists... UserCache must clone all users it return to prevent
            // concurrency issues (e.g., a caller might indirectly modify a cached User's group list, leading to stale credentials)
            for (User user : allUsers)
            {
                Integer userId = user.getUserId();
                userIdMap.put(userId, user);

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
                {
                    activeUsers.add(user);
                    activeEmails.add(user.getEmail());  // This list will be sorted, since allUsers is sorted by email address
                }
            }

            return new UserCollections(userIdMap, emailMap, activeUsers, activeEmails);
        }
    }
}
