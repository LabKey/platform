/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.study.StudyCachable;

/**
 * User: brittp
 * Date: Feb 21, 2006
 * Time: 10:19:30 AM
 */
public class StudyCache
{
    private static String getCacheName(Container c, @Nullable Object cacheKey)
    {
        return c.getId() + "/" + (null != cacheKey ? cacheKey : "");
    }

    public static void cache(TableInfo tinfo, Container c, String objectId, StudyCachable cachable)
    {
        if (cachable != null)
            cachable.lock();
        DbCache.put(tinfo, getCacheName(c, objectId), cachable, CacheManager.HOUR);
    }

    public static void cache(TableInfo tinfo, Container c, Object cacheKey, StudyCachable cachable)
    {
        // We allow caching of null values: 
        if (cachable != null)
            cachable.lock();
        DbCache.put(tinfo, getCacheName(c, cacheKey), cachable, CacheManager.HOUR);
    }

    public static void uncache(TableInfo tinfo, Container c, Object cacheKey)
    {
        DbCache.remove(tinfo, getCacheName(c, cacheKey));
    }

    public static Object getCached(TableInfo tinfo, Container c, Object cacheKey)
    {
        return DbCache.get(tinfo, getCacheName(c, cacheKey));
    }

    public static Object get(TableInfo tinfo, Container c, Object cacheKey, CacheLoader<String, Object> loader)
    {
        // Don't use a BlockingCache as thats can cause deadlocks when needing to do a
        // load when all other DB connections are in use in threads, including one
        // that holds the BlockingCache's lock
        DatabaseCache<Object> cache2 = DbCache.getCacheGeneric(tinfo);
        return cache2.get(getCacheName(c, cacheKey), null, loader);
    }

    public static void clearCache(TableInfo tinfo, Container c)
    {
        DbCache.removeUsingPrefix(tinfo, getCacheName(c, null));
    }
}
