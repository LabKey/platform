/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

package org.labkey.api.cache;

import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Path;

import java.util.HashMap;
import java.util.Map;

/**
 * DbCache is a wrapper that allocates a shared transaction-aware cache per TableInfo (for non-transaction use)
 * and a private transaction-aware cache per TableInfo per thread/connection (used for the duration of a transaction).
 *
 * Use CacheManager.getCache() or DatabaseCache instead.
 *
 * User: migra
 * Date: Nov 30, 2005
 */
@Deprecated
public class DbCache
{
    private static final Map<Path, DatabaseCache<Object>> CACHES = new HashMap<>(100);

    public static int DEFAULT_CACHE_SIZE = 1000;   // Each TableInfo can override this (see tableInfo.xsd <cacheSize> element)


    public static <K> DatabaseCache<K> getCacheGeneric(TableInfo tinfo)
    {
        return (DatabaseCache<K>)getCache(tinfo, true);
    }


    private static DatabaseCache<Object> getCache(TableInfo tinfo, boolean create)
    {
        Path cacheKey = tinfo.getNotificationKey();
        assert null != cacheKey : "DbCache not supported for " + tinfo.toString();

        synchronized(CACHES)
        {
            DatabaseCache<Object> cache = CACHES.get(cacheKey);

            if (null == cache && create)
            {
                cache = new DatabaseCache<>(tinfo.getSchema().getScope(), tinfo.getCacheSize(), "DbCache: " + tinfo.getName());
                CACHES.put(cacheKey, cache);
            }

            return cache;
        }
    }


    public static void put(TableInfo tinfo, String name, Object obj)
    {
        DatabaseCache<Object> cache = getCache(tinfo, true);
        cache.put(name, obj);
    }


    public static void put(TableInfo tinfo, String name, Object obj, long millisToLive)
    {
        DatabaseCache<Object> cache = getCache(tinfo, true);
        cache.put(name, obj, millisToLive);
    }


    public static Object get(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        return null == cache ? null : cache.get(name);
    }
    

    public static void remove(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.remove(name);
    }


    /** used by Table */
    public static void invalidateAll(TableInfo tinfo)
    {
        DatabaseCache cache = CACHES.get(tinfo.getNotificationKey());
        if (null != cache)
            cache.clear();
    }


    public static void clear(TableInfo tinfo)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.clear();
    }


    public static void removeUsingPrefix(TableInfo tinfo, String name)
    {
        DatabaseCache cache = getCache(tinfo, false);
        if (null != cache)
            cache.removeUsingPrefix(name);
    }
}
