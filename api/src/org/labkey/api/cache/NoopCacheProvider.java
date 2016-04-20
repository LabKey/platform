package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

import static org.labkey.api.cache.CacheType.DeterministicLRU;

/**
 * Created by adam on 4/19/2016.
 */
public class NoopCacheProvider implements CacheProvider
{
    @Override
    public <K, V> SimpleCache<K, V> getSimpleCache(String debugName, int limit, long defaultTimeToLive, long defaultTimeToIdle, boolean temporary)
    {
        return new NoopCache<>();
    }

    @Override
    public void shutdown()
    {
    }

    private static class NoopCache<K, V> implements SimpleCache<K, V>
    {
        @Override
        public void put(K key, V value)
        {
        }

        @Override
        public void put(K key, V value, long timeToLive)
        {
        }

        @Nullable
        @Override
        public V get(K key)
        {
            return null;
        }

        @Override
        public void remove(K key)
        {
        }

        @Override
        public int removeUsingFilter(Filter<K> filter)
        {
            return 0;
        }

        @Override
        public void clear()
        {
        }

        @Override
        public int getLimit()
        {
            return 0;
        }

        @Override
        public int size()
        {
            return 0;
        }

        @Override
        public boolean isEmpty()
        {
            return false;
        }

        @Override
        public long getDefaultExpires()
        {
            return 0;
        }

        @Override
        public void close()
        {
        }

        @Override
        public CacheType getCacheType()
        {
            return DeterministicLRU;
        }

        @Override
        public void log()
        {
        }
    }
}
