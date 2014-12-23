/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: adam
 * Date: 11/30/12
 * Time: 10:46 PM
 */
public class PropertyCache
{
    private final BlockingStringKeyCache<Map<String, String>> _blockingCache;

    PropertyCache(String name, CacheLoader<String, Map<String, String>> propertyLoader)
    {
        _blockingCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, name, propertyLoader);
    }

    @Nullable Map<String, String> getProperties(User user, Container container, String category)
    {
        String key = getCacheKey(container, user, category);

        return _blockingCache.get(key, new Object[]{container, user, category});
    }

    void remove(PropertyMap map)
    {
        Object[] params = map.getCacheParams();

        _blockingCache.remove(getCacheKey((String) params[0], (Integer) params[1], (String) params[2]));
    }

    void removeAll(Container c)
    {
        _blockingCache.removeUsingPrefix(c.getId());
    }

    void remove(Container container, User user, String category)
    {
        _blockingCache.remove(getCacheKey(container.getId(), user.getUserId(), category));
    }

    private static String getCacheKey(Container c, User user, String category)
    {
        return getCacheKey(c.getId(), user.getUserId(), category);
    }

    private static String getCacheKey(String containerId, int userId, String category)
    {
        return String.valueOf(containerId) + "/" + String.valueOf(userId) + "/" + category;
    }
}
