/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
import org.labkey.api.util.Filter;

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
    protected final Cache<K, Object> _cache;
    protected final CacheLoader<K, V> _loader;

    public static final Object UNINITIALIZED = new Object() {public String toString() { return "UNINITIALIZED";}};


    public BlockingCache(Cache<K, Object> cache)
    {
        this(cache, null);
    }


    // TODO: Should take Cache<K, Wrapper<V>> instead (need to expose Wrapper)
    public BlockingCache(Cache<K, Object> cache, @Nullable CacheLoader<K, V> loader)
    {
        _cache = cache;
        _loader = loader;
    }


    protected static class Wrapper<V>
    {
        @SuppressWarnings({"unchecked"})
        protected V value = (V)UNINITIALIZED;

        void setValue(V v)
        {
            value = v;
        }

        public V getValue()
        {
            return value == UNINITIALIZED ? null : value;
        }
    }


    protected Wrapper<V> createWrapper()
    {
        return new Wrapper<V>();
    }


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
    public V get(K key, @Nullable Object arg, CacheLoader<K, V> loader)
    {
        Wrapper<V> w;
        synchronized(_cache)
        {
            w = (Wrapper<V>)_cache.get(key);
            if (null == w)
                _cache.put(key, w = createWrapper());
        }

        // there is a chance the wrapper can be removed from the cache
        // we don't guarantee that two objects can't be loaded concurrently for the same key,
        // just that it's unlikely
        
        synchronized (w)
        {
            if (UNINITIALIZED != w.value)
            {
                if (isValid(w, key, arg, loader))
                    return w.getValue();
            }

            V value = loader.load(key, arg);
            w.setValue(value);
            return value;
        }
    }


    @Override
    public void put(K key, V value)
    {
        throw new UnsupportedOperationException("use get(loader)");
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
    public int getLimit()
    {
        return _cache.getLimit();
    }

    @Override
    public int size()
    {
        return _cache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _cache.getDefaultExpires();
    }

    @Override
    public CacheType getCacheType()
    {
        return _cache.getCacheType();
    }

    @Override
    public void close()
    {
        _cache.close();
    }
}
