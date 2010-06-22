/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.TableInfo;
import org.labkey.study.model.StudyCachable;

/**
 * User: brittp
 * Date: Feb 21, 2006
 * Time: 10:19:30 AM
 */
public class StudyCache
{
    private static boolean ENABLE_CACHING = true;

    private static String getCacheName(String containerId, Object cacheKey)
    {
        return containerId + "/" + cacheKey;
    }

    public static void cache(TableInfo tinfo, String containerId, String objectId, StudyCachable cachable)
    {
        if (cachable != null)
            cachable.lock();
        if (!ENABLE_CACHING)
            return;
        DbCache.put(tinfo, getCacheName(containerId, objectId), cachable, CacheManager.HOUR);
    }

    public static void cache(TableInfo tinfo, String containerId, String objectId, StudyCachable[] cachables)
    {
        for (StudyCachable cachable : cachables)
        {
            if (cachable != null)
                cachable.lock();
        }
        if (!ENABLE_CACHING)
            return;
        DbCache.put(tinfo, getCacheName(containerId, objectId), cachables, CacheManager.HOUR);
    }

    public static void cache(TableInfo tinfo, String containerId, Object cacheKey, StudyCachable cachable)
    {
        // We allow caching of null values: 
        if (cachable != null)
            cachable.lock();
        if (!ENABLE_CACHING)
            return;
        DbCache.put(tinfo, getCacheName(containerId, cacheKey), cachable, CacheManager.HOUR);
    }

    public static void uncache(TableInfo tinfo, String containerId, Object cacheKey)
    {
        if (!ENABLE_CACHING)
            return;
        DbCache.remove(tinfo, getCacheName(containerId, cacheKey));
    }

    public static Object getCached(TableInfo tinfo, String containerId, Object cacheKey)
    {
        if (!ENABLE_CACHING)
            return null;
        return DbCache.get(tinfo, getCacheName(containerId, cacheKey));
    }

    public static void clearCache(TableInfo tinfo, String containerId)
    {
        if (!ENABLE_CACHING)
            return;
        // TODO: this clear call is too heavy-handed.
        //it will clear the cache for all containers, not just the one we care about.
        DbCache.clear(tinfo);
    }
}
