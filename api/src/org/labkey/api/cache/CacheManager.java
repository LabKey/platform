/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.ehcache.EhCacheProvider;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.mbean.LabKeyManagement;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:05:07 AM
 */

public class CacheManager
{
    private static final Logger LOG = LogHelper.getLogger(CacheManager.class, "General cache information");

    // TODO: Millisecond granularity seems misleading (EhCache uses seconds) and silly
    public static final long SECOND = DateUtils.MILLIS_PER_SECOND;
    public static final long MINUTE = DateUtils.MILLIS_PER_MINUTE;
    public static final long HOUR = DateUtils.MILLIS_PER_HOUR;
    public static final long DAY = DateUtils.MILLIS_PER_DAY;
    public static final long WEEK = DAY * 7;
    public static final long MONTH = DAY * 30;
    public static final long YEAR = DAY * 365;

    public static final long DEFAULT_TIMEOUT = HOUR;
    public static final int DEFAULT_CACHE_SIZE = 5000;

    // Set useCache = false to completely disable all caching... and slow your server to a near halt. Possibly useful for
    // reproducing CacheLoader re-entrancy problems, but not much else.
    private static final boolean useCache = true;
    private static final CacheProvider PROVIDER = useCache ? EhCacheProvider.getInstance() : new NoopCacheProvider();

    private static final List<TrackingCache<?, ?>> KNOWN_CACHES = new LinkedList<>();

    private static final List<CacheListener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Marker indicating unlimited entries or unlimited time-to-live (do not expire entries) */
    public static final int UNLIMITED = 0;

    private static <K, V> TrackingCache<K, V> createCache(int limit, long defaultTimeToLive, String debugName)
    {
        CacheWrapper<K, V> cache = new CacheWrapper<>(PROVIDER.getSimpleCache(debugName, limit, defaultTimeToLive, UNLIMITED, false), debugName, null, Thread.currentThread().getStackTrace());
        addToKnownCaches(cache);  // Permanent cache -- hold onto it
        LabKeyManagement.register(cache.createDynamicMBean(), debugName, "Cache");
        return cache;
    }

    public static <K, V> TrackingCache<K, V> getCache(int limit, long defaultTimeToLive, String debugName)
    {
        return createCache(limit, defaultTimeToLive, debugName);
    }

    public static <V> Cache<String, V> getStringKeyCache(int limit, long defaultTimeToLive, String debugName)
    {
        return createCache(limit, defaultTimeToLive, debugName);
    }

    public static <K, V> BlockingCache<K, V> getBlockingCache(int limit, long defaultTimeToLive, String debugName, @Nullable CacheLoader<K, V> loader)
    {
        TrackingCache<K, Wrapper<V>> cache = getCache(limit, defaultTimeToLive, debugName);
        return new BlockingCache<>(cache, loader);
    }

    public static <V> BlockingCache<String, V> getBlockingStringKeyCache(int limit, long defaultTimeToLive, String debugName, @Nullable CacheLoader<String, V> loader)
    {
        Cache<String, Wrapper<V>> cache = getStringKeyCache(limit, defaultTimeToLive, debugName);
        return new BlockingCache<>(cache, loader);
    }

    // Temporary caches must be closed when no longer needed. Their statistics can accumulate to another cache's stats.
    public static <K, V> Cache<K, V> getTemporaryCache(int limit, long defaultTimeToLive, String debugName, @Nullable Stats stats)
    {
        return new CacheWrapper<>(PROVIDER.getSimpleCache(debugName, limit, defaultTimeToLive, UNLIMITED, true), debugName, stats, null);
    }

    private static final Cache<String, Object> SHARED_CACHE = getStringKeyCache(10000, DEFAULT_TIMEOUT, "Shared");

    public static <V> Cache<String, V> getSharedCache()
    {
        return (Cache<String, V>)SHARED_CACHE;
    }

    // We hold onto "permanent" caches so memtracker can clear them and admin console can report statistics on them
    private static void addToKnownCaches(TrackingCache<?, ?> cache)
    {
        synchronized (KNOWN_CACHES)
        {
            KNOWN_CACHES.add(cache);

            LOG.debug("Known caches: " + KNOWN_CACHES.size());
        }
    }

    public static void clearAllKnownCaches()
    {
        synchronized (KNOWN_CACHES)
        {
            KNOWN_CACHES.forEach(TrackingCache::clear);
        }

        fireClearCaches();
    }

    // Return a copy of KNOWN_CACHES for reporting statistics
    public static List<TrackingCache<?, ?>> getKnownCaches()
    {
        synchronized (KNOWN_CACHES)
        {
            return new ArrayList<>(KNOWN_CACHES);
        }
    }

    private static void fireClearCaches()
    {
        LISTENERS.forEach(CacheListener::clearCaches);
    }

    public static void addListener(CacheListener listener)
    {
        LISTENERS.add(listener);
    }

    public static void removeListener(CacheListener listener)
    {
        LISTENERS.remove(listener);
    }

    public static CacheStats getCacheStats(TrackingCache<?, ?> cache)
    {
        return new CacheStats(cache, cache.getStats(), cache.size());
    }

    public static CacheStats getTransactionCacheStats(TrackingCache<?, ?> cache)
    {
        return new CacheStats(cache, cache.getTransactionStats(), 0);
    }

    public static void shutdown()
    {
        PROVIDER.shutdown();
    }

    // Validate a cached value. For now, just log warnings for mutable collections.
    public static <V> void validate(String debugName, @Nullable V value)
    {
        if (value instanceof Wrapper<?>)
            return;

        String description = CollectionUtils.getModifiableCollectionMapOrArrayType(value);

        if (null != description)
        {
            LOG.warn(debugName + " attempted to cache " + description + ", which could be mutated by callers!");
        }
    }


    /* This interface allows a Collection to declare itself immutable when being added to a cache */
    public interface Sealable
    {
        boolean isSealed();
    }
}
