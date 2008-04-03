package org.labkey.study;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.DbCache;
import org.labkey.api.util.Cache;
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
        DbCache.put(tinfo, getCacheName(containerId, objectId), cachable, Cache.HOUR);
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
        DbCache.put(tinfo, getCacheName(containerId, objectId), cachables, Cache.HOUR);
    }

    public static void cache(TableInfo tinfo, String containerId, Object cacheKey, StudyCachable cachable)
    {
        if (cachable == null)
            return;
        cachable.lock();
        if (!ENABLE_CACHING)
            return;
        DbCache.put(tinfo, getCacheName(containerId, cacheKey), cachable, Cache.HOUR);
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
