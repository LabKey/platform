package org.labkey.api.cache;

import org.apache.commons.lang.time.DateUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:05:07 AM
 */
public class CacheManager
{
    public static final long SECOND = DateUtils.MILLIS_PER_SECOND;
    public static final long MINUTE = DateUtils.MILLIS_PER_MINUTE;
    public static final long HOUR = DateUtils.MILLIS_PER_HOUR;
    public static final long DAY = DateUtils.MILLIS_PER_DAY;

    public static final long DEFAULT_TIMEOUT = HOUR;

    // TODO: Change this to List<Cache> once we eliminate direct usages of TTLCacheMap and CacheMap
    private static final List<Clearable> KNOWN_CACHES = new LinkedList<Clearable>();

    public static <K, V> Cache<K, V> getCache(int size, long defaultTimeToLive, String debugName)
    {
        Cache<K, V> cache = new CacheImpl<K, V>(size, defaultTimeToLive, debugName, null);
        addToKnownCaches(cache);
        return cache;
    }

    public static <V> StringKeyCache<V> getStringKeyCache(int size, long defaultTimeToLive, String debugName)
    {
        StringKeyCacheImpl<V> cache = new StringKeyCacheImpl<V>(size, defaultTimeToLive, debugName, null);
        addToKnownCaches(cache);
        return cache;
    }

    public static <V> StringKeyCache<V> getTemporaryCache(int size, long defaultTimeToLive, String debugName, @Nullable Stats stats)
    {
        // Temporary caches are not tracked and their statistics can accumulate to another cache's stats
        return new StringKeyCacheImpl<V>(size, defaultTimeToLive, debugName, stats);
    }

    private static final StringKeyCache<Object> SHARED_CACHE = getStringKeyCache(10000, DEFAULT_TIMEOUT, "sharedCache");

    public static <V> StringKeyCache<V> getSharedCache()
    {
        return (StringKeyCache<V>)SHARED_CACHE;
    }

    // We track "permanent" caches so memtracker can clear them and admin console can report statistics
    private static void addToKnownCaches(Clearable cache)
    {
        synchronized (KNOWN_CACHES)
        {
            KNOWN_CACHES.add(cache);
        }
    }

    public static void clearAllKnownCaches()
    {
        synchronized (KNOWN_CACHES)
        {
            for (Clearable cache : KNOWN_CACHES)
                cache.clear();
        }
    }

    // We report statistics only for the Cache instances
    public static List<Cache> getKnownCaches()
    {
        List<Cache> copy = new ArrayList<Cache>();

        synchronized (KNOWN_CACHES)
        {
            for (Clearable clearable : KNOWN_CACHES)
            {
                if (clearable instanceof Cache)
                    copy.add((Cache)clearable);
            }
        }

        return copy;
    }

    public static CacheStats getCacheStats(Cache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getStats(), cache.size(), cache.getLimit());
    }

    public static CacheStats getTransactionCacheStats(Cache cache)
    {
        return new CacheStats(cache.getDebugName(), cache.getTransactionStats(), cache.size(), cache.getLimit());
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getCacheMap(int initialSize, String debugName)
    {
        CacheMap<K, V> cache = new CacheMap<K, V>(initialSize, debugName, null);
        addToKnownCaches(cache);
        return cache;
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getLimitedCacheMap(int initialSize, int limit, String debugName)
    {
        CacheMap<K, V> cache = new LimitedCacheMap<K, V>(initialSize, limit, debugName);
        addToKnownCaches(cache);
        return cache;
    }

    @Deprecated
    public static <K, V> CacheMap<K, V> getTTLCacheMap(int limit, String debugName)
    {
        TTLCacheMap<K, V> cache = new TTLCacheMap<K, V>(limit, debugName);
        addToKnownCaches(cache);
        return cache;
    }
}
