package org.labkey.announcements.model;

import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;

/**
 * Created by Marty on 2/25/2015.
 */
public class TourCache
{
    private static final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(50000, CacheManager.DAY, "Tours", null);

    public abstract static class TourCacheLoader<V> implements CacheLoader<String, V>
    {
        abstract V load(String key, Container c);

        @Override
        public V load(String key, Object argument)
        {
            return load(key, (Container)argument);
        }
    }

    static TourCollections getTourCollections(Container c, TourCacheLoader<TourCollections> loader)
    {
        return get(c, loader);
    }

    public static void uncache(Container c)
    {
        BLOCKING_CACHE.removeUsingPrefix(getCacheKey(c));
    }

    // Private methods below

    private static String getCacheKey(Container c)
    {
        return "tours/" + c.getId();
    }

    private static <V> V get(Container c, TourCacheLoader<V> loader)
    {
        if (c == null)
            return null;

        return (V)BLOCKING_CACHE.get(getCacheKey(c), c, (TourCacheLoader<Object>)loader);
    }

}
