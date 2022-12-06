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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.DeadlockPreventingException;
import org.labkey.api.util.Filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a decorator for any Cache instance, it will provide for synchronizing object load
 * (readers block while someone is creating an object)
 * User: matthewb
 * Date: Sep 16, 2010
 */
public class BlockingCache<K, V> implements Cache<K, V>
{
    protected final Cache<K, Wrapper<V>> _cache;
    protected final CacheLoader<K, V> _loader;
    protected CacheTimeChooser<K> _cacheTimeChooser;
    /**
     * Milliseconds to wait if some other thread is loading the cache before timing out.
     * Note that we will NOT timeout the thread that is doing the load, but this can still help reduce deadlocks
     */
    protected final long _timeout;

    public static final Object UNINITIALIZED = new Object() {public String toString() { return "UNINITIALIZED";}};


    public BlockingCache(Cache<K, Wrapper<V>> cache)
    {
        this(cache, null);
    }

    public BlockingCache(Cache<K, Wrapper<V>> cache, @Nullable CacheLoader<K, V> loader)
    {
        this(cache, loader, TimeUnit.MINUTES.toMillis(5));
    }

    public BlockingCache(Cache<K, Wrapper<V>> cache, @Nullable CacheLoader<K, V> loader, long timeout)
    {
        _cache = cache;
        _loader = loader;
        _timeout = timeout;
    }


    public void setCacheTimeChooser(CacheTimeChooser<K> cacheTimeChooser)
    {
        _cacheTimeChooser = cacheTimeChooser;
    }


    protected Wrapper<V> createWrapper()
    {
        return new Wrapper<>();
    }

    protected boolean isInitialized(Wrapper<V> w)
    {
        return w.value != UNINITIALIZED;
    }


    public V get(@NotNull K key, @Nullable Object argument)
    {
        if (null == _loader)
            throw new IllegalStateException("Set loader before calling this method");
        return get(key, argument, _loader);    
    }


    @Override
    public V get(@NotNull K key, @Nullable Object argument, CacheLoader<K, V> loader)
    {
        Wrapper<V> w;

        synchronized(_cache)
        {
            w = _cache.get(key);
            if (null == w)
            {
                w = createWrapper();
                Long ttl;

                // Override the default TTL if a CacheTimeChooser is present and provides a custom value
                if (null == _cacheTimeChooser || null == (ttl = _cacheTimeChooser.getTimeToLive(key, argument)))
                    _cache.put(key, w);
                else
                    _cache.put(key, w, ttl);
            }
        }

        // there is a chance the wrapper can be removed from the cache
        // we don't guarantee that two objects can't be loaded concurrently for the same key,
        // just that it's unlikely

        synchronized (w.getLockObject())
        {
            if (isInitialized(w))
                return w.getValue();

            long endTime = _timeout > 0 ? System.currentTimeMillis() + _timeout : Long.MAX_VALUE;
            while (w.isLoading())
            {
                if (System.currentTimeMillis() > endTime)
                {
                    throw new DeadlockPreventingException("Cache timeout for " + getTrackingCache().getDebugName() + ", exceeding " + _timeout + "ms limit for cache key " + key);
                }
                try
                {
                    // Wait either 1 second or 1 minute at a time, depending on the timeout value
                    long waitTime = _timeout > 0 && _timeout < TimeUnit.MINUTES.toMillis(1) ? TimeUnit.SECONDS.toMillis(1) : TimeUnit.MINUTES.toMillis(1);
                    w.getLockObject().wait(waitTime);
                }
                catch (InterruptedException x)
                {/* */}
            }

            if (isInitialized(w))
                return w.getValue();

                // if we fall through here it means there _could_ be two threads trying to load the same object
                // the cache loader needs to support that

            w.setLoading();
        }

        boolean success = false;

        try
        {
            if (null == loader)
                loader = _loader;

            if (null == loader)
                throw new IllegalStateException("cache loader was not provided");

            V value = loader.load(key, argument);
            CacheManager.validate("BlockingCache over \"" + _cache + "\" cache", value);

            synchronized (w.getLockObject())
            {
                w.setValue(value);
                w.getLockObject().notifyAll();
            }
            success = true;
            return value;
        }
        finally
        {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            if (!success)
            {
                synchronized (w.getLockObject())
                {
                    w.doneLoading();
                    w.getLockObject().notifyAll();
                }
            }
        }
    }


    /**
     * Preload or replace existing value at this key. Similar to remove() followed by get(), but doesn't block get() callers
     * and leaves existing value in place if load fails for any reason. This helps in cases of long-running and/or unreliable
     * loads. Most callers would want to refresh() on a background thread.
     */
    public void refresh(@NotNull K key, @Nullable Object argument)
    {
        if (null == _loader)
            throw new IllegalStateException("cache loader is not set");

        V value = _loader.load(key, argument);

        put(key, value);
    }


    @Override
    public void put(@NotNull K key, final V value)
    {
        // Perhaps a better approach would be to create a private version of get() that takes a "force" flag, but doesn't seem worth it
        remove(key);
        get(key, null, (key1, argument) -> value);
    }


    @Override
    public void put(@NotNull K key, V value, long timeToLive)
    {
        throw new UnsupportedOperationException("use get(loader)");
    }


    @Override
    public V get(@NotNull K key)
    {
        return get(key, null);
    }


    @Override
    public void remove(@NotNull K key)
    {
        _cache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        return _cache.removeUsingFilter(filter);
    }

    @Override
    public Set<K> getKeys()
    {
        return _cache.getKeys();
    }

    @Override
    public void clear()
    {
        _cache.clear();
    }

    @Override
    public void close()
    {
        _cache.close();
    }

    @Override
    public TrackingCache getTrackingCache()
    {
        return _cache.getTrackingCache();
    }

    @Override
    public Cache<K, V> createTemporaryCache()
    {
        return new BlockingCache<>(_cache.createTemporaryCache(), _loader, _timeout);
    }

    public static class BlockingCacheTest extends Assert
    {
        private Cache<Integer, Wrapper<Integer>> _cache;
        private final Map<Integer, Wrapper<Integer>> _map = new HashMap<>();

        @Before
        public void setUp()
        {
            _cache = new Cache<>()
            {
                @Override public void put(@NotNull Integer key, Wrapper<Integer> value)
                {
                    _map.put(key, value);
                }
                @Override public void put(@NotNull Integer key, Wrapper<Integer> value, long timeToLive)
                {
                    _map.put(key, value);
                }
                @Override public Wrapper<Integer> get(@NotNull Integer key)
                {
                    return _map.get(key);
                }
                @Override public Wrapper<Integer> get(@NotNull Integer key, @Nullable Object arg, CacheLoader<Integer, Wrapper<Integer>> loader) { throw new UnsupportedOperationException(); }
                @Override public void remove(@NotNull Integer key) { throw new UnsupportedOperationException(); }
                @Override public int removeUsingFilter(Filter<Integer> filter) { throw new UnsupportedOperationException(); }
                @Override public Set<Integer> getKeys() { throw new UnsupportedOperationException(); }
                @Override public void clear() { throw new UnsupportedOperationException(); }
                @Override public void close() { throw new UnsupportedOperationException(); }
                @Override public TrackingCache getTrackingCache() { throw new UnsupportedOperationException(); }
                @Override public Cache<Integer, Wrapper<Integer>> createTemporaryCache() { throw new UnsupportedOperationException(); }
            };
        }

        private CacheLoader<Integer, Integer> createLoader(final AtomicInteger calls, final int sleepTime)
        {
            return (key, argument) -> {
                calls.incrementAndGet();
                try {Thread.sleep(sleepTime);}catch(InterruptedException x){/* */}
                return key*key;
            };
        }

        @Test
        public void testBlockingGet()
        {
            final AtomicInteger calls = new AtomicInteger();
            final BlockingCache<Integer,Integer> bc = new BlockingCache<>(_cache, createLoader(calls, 1000));
            final Object start = new Object();
            Runnable r = () -> {
                Random r1 = new Random();
                synchronized (start) { try{start.wait(1000);}catch(InterruptedException x){/* */} }
                for (int i=0 ; i<100 ; i++)
                {
                    int k = r1.nextInt();
                    bc.get(Math.abs(k % 5));
                }
            };
            createAndStartThreads(r, start, 10);
            assertEquals(5, calls.get());
            assertEquals(5, _map.size());
        }

        @NotNull
        private void createAndStartThreads(Runnable r, Object start, int count)
        {
            Thread[] threads = new Thread[count];
            for (int i=0 ; i < threads.length ; i++)
                threads[i] = new Thread(r);
            for (Thread thread : threads)
                thread.start();
            Thread.yield();
            synchronized (start) { start.notifyAll(); }
            for (Thread thread : threads)
                try { thread.join(); } catch (InterruptedException x) {}
        }

        @Test
        public void testTimeout()
        {
            final AtomicInteger calls = new AtomicInteger();
            final BlockingCache<Integer, Integer> bc = new BlockingCache<>(_cache, createLoader(calls, 5000), 1000);

            final AtomicInteger timeouts = new AtomicInteger();
            final Object start = new Object();
            Runnable r = () -> {
                synchronized (start) { try{start.wait(1000);}catch(InterruptedException x){/* */} }
                try
                {
                    bc.get(1000);
                }
                catch (RuntimeException e)
                {
                    timeouts.incrementAndGet();
                }
            };

            createAndStartThreads(r, start, 10);

            assertEquals(1, calls.get());
            assertEquals(9, timeouts.get());
        }
    }
}
