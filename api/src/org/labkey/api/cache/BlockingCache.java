/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.Filter;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: matthewb
 * Date: Sep 16, 2010
 * Time: 4:42:07 PM
 *
 * This is a decorator for any Cache instance, it will provide for synchronizing object load
 * (readers block while someone is creating an object)
 */
public class BlockingCache<K, V> implements Cache<K, V>
{
    protected final Cache<K, Wrapper<V>> _cache;
    protected final CacheLoader<K, V> _loader;
    protected CacheTimeChooser<K> _cacheTimeChooser;

    public static final Object UNINITIALIZED = new Object() {public String toString() { return "UNINITIALIZED";}};


    public BlockingCache(Cache<K, Wrapper<V>> cache)
    {
        this(cache, null);
    }


    public BlockingCache(Cache<K, Wrapper<V>> cache, @Nullable CacheLoader<K, V> loader)
    {
        _cache = cache;
        _loader = loader;
    }


    public void setCacheTimeChooser(CacheTimeChooser<K> cacheTimeChooser)
    {
        _cacheTimeChooser = cacheTimeChooser;
    }


    protected Wrapper<V> createWrapper()
    {
        return new Wrapper<>();
    }


    // TODO: Remove or at least rename & change signature? Nobody overloads it any more.
    protected boolean isValid(Wrapper<V> w, K key, Object argument, CacheLoader loader)
    {
        return w.value != UNINITIALIZED;
    }


    public V get(K key, @Nullable Object argument)
    {
        if (null == _loader)
            throw new IllegalStateException("Set loader before calling this method");
        return get(key, argument, _loader);    
    }


    @Override
    public V get(K key, @Nullable Object argument, CacheLoader<K, V> loader)
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
            if (isValid(w, key, argument, loader))
                return w.getValue();

            while (w.isLoading())
            {
                try
                {
                    w.getLockObject().wait(TimeUnit.MINUTES.toMillis(1));
                }
                catch (InterruptedException x)
                {/* */}
            }

            if (isValid(w , key, argument, loader))
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
            CacheManager.validate(loader, value);

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


    @Override
    public void put(K key, final V value)
    {
        get(key, null, new CacheLoader<K, V>()
        {
            @Override
            public V load(K key, @Nullable Object argument)
            {
                return value;
            }
        });
    }


    @Override
    public void put(K key, V value, long timeToLive)
    {
        throw new UnsupportedOperationException("use get(loader)");
    }


    @Override
    public V get(K key)
    {
        return get(key, null);
    }


    @Override
    public void remove(K key)
    {
        _cache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        return _cache.removeUsingFilter(filter);
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


    public static class BlockingCacheTest extends Assert
    {
        @Test
        public void testBlockingGet()
        {
            final HashMap<Integer,Wrapper<Integer>> map = new HashMap<>();
            Cache<Integer,Wrapper<Integer>> cache = new Cache<Integer,Wrapper<Integer>>()
            {
                @Override
                public void put(Integer key, Wrapper<Integer> value)
                {
                    map.put(key, value);
                }
                @Override
                public void put(Integer key, Wrapper<Integer> value, long timeToLive)
                {
                    map.put(key, value);
                }
                @Override
                public Wrapper<Integer> get(Integer key)
                {
                    return map.get(key);
                }
                @Override public Wrapper<Integer> get(Integer key, @Nullable Object arg, CacheLoader<Integer, Wrapper<Integer>> loader) { throw new UnsupportedOperationException(); }
                @Override public void remove(Integer key) { throw new UnsupportedOperationException(); }
                @Override public int removeUsingFilter(Filter<Integer> filter) { throw new UnsupportedOperationException(); }
                @Override public void clear() { throw new UnsupportedOperationException(); }
                @Override public void close() { throw new UnsupportedOperationException(); }
                @Override public TrackingCache getTrackingCache() { throw new UnsupportedOperationException(); }
            };
            final AtomicInteger calls = new AtomicInteger();
            CacheLoader<Integer,Integer> loader = new CacheLoader<Integer, Integer>()
            {
                @Override
                public Integer load(Integer key, @Nullable Object argument)
                {
                    calls.incrementAndGet();
                    try {Thread.sleep(1000);}catch(InterruptedException x){/* */}
                    return key*key;
                }
            };
            final BlockingCache<Integer,Integer> bc = new BlockingCache<>(cache,loader);
            final Object start = new Object();
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    Random r = new Random();
                    synchronized (start) { try{start.wait(1000);}catch(InterruptedException x){/* */} }
                    for (int i=0 ; i<100 ; i++)
                    {
                        int k = r.nextInt();
                        bc.get(Math.abs(k % 5));
                    }
                }
            };
            Thread[] threads = new Thread[10];
            for (int i=0 ; i<10 ; i++)
                threads[i] = new Thread(r);
            for (int i=0 ; i<10 ; i++)
                threads[i].start();
            Thread.yield();
            synchronized (start) { start.notifyAll(); }
            for (int i=0 ; i<10 ; i++)
                try { threads[i].join(); } catch (InterruptedException x) {}
            assertEquals(5, calls.get());
            assertEquals(5, map.size());
        }
    }
}
