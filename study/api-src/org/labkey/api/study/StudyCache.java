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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Path;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class StudyCache
{
    // TODO: Generics!
    // TODO: Switch to BlockingCache
    private static final Map<Path, DatabaseCache<String, Object>> CACHES = new HashMap<>(10);

    private static DatabaseCache<String, Object> getCache(TableInfo tinfo, boolean create)
    {
        Path cacheKey = tinfo.getNotificationKey();
        assert null != cacheKey : "DbCache not supported for " + tinfo;

        synchronized(CACHES)
        {
            DatabaseCache<String, Object> cache = CACHES.get(cacheKey);

            if (null == cache && create)
            {
                cache = new DatabaseCache(tinfo.getSchema().getScope(), tinfo.getCacheSize(), "StudyCache: " + tinfo.getName());
                CACHES.put(cacheKey, cache);
            }

            return cache;
        }
    }

    private static String getCacheName(Container c, @Nullable Object cacheKey)
    {
        return c.getId() + "/" + (null != cacheKey ? cacheKey : "");
    }

    public static void cache(TableInfo tinfo, Container c, String objectId, StudyCachable cachable)
    {
        if (cachable != null)
            cachable.lock();
        DbCache.put(tinfo, getCacheName(c, objectId), cachable, CacheManager.HOUR);
        DatabaseCache<String, Object> cache = getCache(tinfo, true);
        cache.put(getCacheName(c, objectId), cachable, CacheManager.HOUR);
    }

    public static void uncache(TableInfo tinfo, Container c, Object cacheKey)
    {
        DbCache.remove(tinfo, getCacheName(c, cacheKey));
        clearCache(tinfo, c); // TODO: this method is broken/inconsistent -- the cacheKey passed in doesn't match the put() keys
    }

    public static Object getCached(TableInfo tinfo, Container c, Object cacheKey)
    {
        Object oldObj = DbCache.get(tinfo, getCacheName(c, cacheKey));
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        Object newObj = cache != null ? cache.get(getCacheName(c, cacheKey)) : null;

        assert Objects.equals(oldObj, newObj);

        return newObj;
    }

    public static Object get(TableInfo tinfo, Container c, Object cacheKey, CacheLoader<String, Object> loader)
    {
        // Don't use a BlockingCache as that can cause deadlocks when needing to do a
        // load when all other DB connections are in use in threads, including one
        // that holds the BlockingCache's lock
        DatabaseCache<String, Object> cache = DbCache.getCache(tinfo, true);
        Object oldObj = cache.get(getCacheName(c, cacheKey), null, loader);
        DatabaseCache<String, Object> cache2 = getCache(tinfo, true);
        Object newObj = cache2 != null ? cache2.get(getCacheName(c, cacheKey), null, loader) : null;

        assert Objects.equals(oldObj, newObj);

        return newObj;
    }

    public static void clearCache(TableInfo tinfo, Container c)
    {
        DbCache.removeUsingPrefix(tinfo, getCacheName(c, null));
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        if (null != cache)
            cache.removeUsingFilter(new Cache.StringPrefixFilter(getCacheName(c, null)));
    }

    public static void clearCache(TableInfo tinfo)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        if (null != cache)
            cache.clear();
    }
}
