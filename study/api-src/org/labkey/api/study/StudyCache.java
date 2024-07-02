/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Path;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StudyCache
{
    // TODO: Cache (and invalidate) a single map per Container for all object types. Fix generics.
    private static final Map<Path, BlockingCache<String, Object>> CACHES = new ConcurrentHashMap<>(10);

    public static @NotNull BlockingCache<String, Object> getCache(TableInfo tinfo)
    {
        Path cacheKey = tinfo.getNotificationKey();
        assert null != cacheKey : "StudyCache not supported for " + tinfo;
        return CACHES.computeIfAbsent(cacheKey, key -> DatabaseCache.get(tinfo.getSchema().getScope(), tinfo.getCacheSize(), "StudyCache: " + tinfo.getName(), null));
    }

    public static String getCacheName(Container c, @Nullable Object cacheKey)
    {
        return c.getId() + "/" + (null != cacheKey ? cacheKey : "");
    }

    public static Object get(TableInfo tinfo, Container c, Object cacheKey, CacheLoader<String, Object> loader)
    {
        return getCache(tinfo).get(getCacheName(c, cacheKey), null, loader);
    }

    public static <T extends StudyCachable<T>> QueryHelper<T>.StudyCacheMap get(TableInfo tinfo, Container c, CacheLoader<String, Object> loader)
    {
        return (QueryHelper<T>.StudyCacheMap)getCache(tinfo).get(c.getId(), null, loader);
    }

    public static void clearCache(TableInfo tinfo, Container c)
    {
        getCache(tinfo).removeUsingFilter(new Cache.StringPrefixFilter(getCacheName(c, null)));
    }

    public static void clearCache(TableInfo tinfo)
    {
        getCache(tinfo).clear();
    }
}
