/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.User;

public class PropertyCache
{
    private final BlockingCache<String, PropertyMap> _blockingCache;

    PropertyCache(String name, CacheLoader<String, PropertyMap> propertyLoader)
    {
        _blockingCache = DatabaseCache.get(CoreSchema.getInstance().getScope(), CacheManager.UNLIMITED, CacheManager.DAY, name, propertyLoader);
    }

    @Nullable PropertyMap getProperties(User user, Container container, String category)
    {
        String key = getCacheKey(container, user, category);

        return _blockingCache.get(key, new Object[]{container, user, category});
    }

    void remove(PropertyMap map)
    {
        _blockingCache.remove(getCacheKey(map.getObjectId(), map.getUser().getUserId(), map.getCategory()));
    }

    void removeAll(Container c)
    {
        _blockingCache.removeUsingFilter(new Cache.StringPrefixFilter(c.getId()));
    }

    void clear()
    {
        _blockingCache.clear();
    }

    private static String getCacheKey(Container c, User user, String category)
    {
        return getCacheKey(c.getId(), user.getUserId(), category);
    }

    private static String getCacheKey(String containerId, int userId, String category)
    {
        return containerId + "/" + userId + "/" + category;
    }
}
