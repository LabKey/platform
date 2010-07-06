package org.labkey.api.cache;

import org.apache.commons.lang.time.DateUtils;
import org.jetbrains.annotations.Nullable;

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

    public static <K, V> Cache<K, V> getCache(int size, long defaultTimeToLive, String debugName)
    {
        return new CacheImpl<K, V>(size, defaultTimeToLive, debugName, true, null);
    }

    public static <V> StringKeyCache<V> getStringKeyCache(int size, long defaultTimeToLive, String debugName)
    {
        return new StringKeyCacheImpl<V>(size, defaultTimeToLive, debugName, true, null);
    }

    public static <V> StringKeyCache<V> getTemporaryCache(int size, long defaultTimeToLive, String debugName, @Nullable Stats stats)
    {
        // Temporary caches are not tracked and their statistics can accumulate to another caches stats
        return new StringKeyCacheImpl<V>(size, defaultTimeToLive, debugName, false, stats);
    }

    private static final StringKeyCache<Object> SHARED_CACHE = getStringKeyCache(10000, DEFAULT_TIMEOUT, "sharedCache");

    public static <V> StringKeyCache<V> getShared()
    {
        return (StringKeyCache<V>)SHARED_CACHE;
    }
}
