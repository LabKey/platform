/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.mbean.CacheMXBean;
import org.labkey.api.util.Filter;
import org.labkey.api.view.ViewServlet;

import javax.management.DynamicMBean;
import javax.management.StandardMBean;
import java.util.Set;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 9:47:03 AM
 */

// TODO: Track expirations?
// Wraps a SimpleCache to provide a full Cache implementation. Adds null markers, loaders, statistics gathering and debug name.
class CacheWrapper<K, V> implements TrackingCache<K, V>, CacheMXBean
{
    private static final Object NULL_MARKER = new Object() {public String toString(){return "MISSING VALUE MARKER";}};

    private final SimpleCache<K, V> _cache;
    private final String _debugName;
    private final Stats _stats;
    private final Stats _transactionStats;
    private final StackTraceElement[] _stackTrace;
    private final V _nullMarker = (V)NULL_MARKER;


    CacheWrapper(@NotNull SimpleCache<K, V> cache, @NotNull String debugName, @Nullable Stats stats)
    {
        _cache = cache;
        assert StringUtils.isNotBlank(debugName);
        _debugName = debugName;
        _stats = (null != stats ? stats : new Stats());
        _transactionStats = new Stats();
        _stackTrace = Thread.currentThread().getStackTrace();
    }


    @Override
    public void put(K key, V value)
    {
        try
        {
            if (null == value)
                value = _nullMarker;

            _cache.put(key, value);
            trackPut(value);
        }
        catch (IllegalStateException ise)
        {
            ViewServlet.checkShuttingDown();
            throw ise;
        }
    }


    @Override
    public void put(K key, V value, long timeToLive)
    {
        try
        {
            if (null == value)
                value = _nullMarker;

            _cache.put(key, value, timeToLive);
            trackPut(value);
        }
        catch (IllegalStateException ise)
        {
            ViewServlet.checkShuttingDown();
            throw ise;
        }
    }


    @Override
    public V get(K key)
    {
        return get(key, null, null);
    }


    @Override
    public V get(K key, @Nullable Object arg, @Nullable CacheLoader<K, V> loader)
    {
        try
        {
            V v = trackGet(_cache.get(key));

            if (null != v)
            {
                v = (v == _nullMarker ? null : v);
            }
            else if (null != loader)
            {
                v = loader.load(key, arg);
                CacheManager.validate(loader, v);
                put(key, v);
            }

            return v;
        }
        catch (IllegalStateException ise)
        {
            ViewServlet.checkShuttingDown();
            throw ise;
        }
    }


    @Override
    public void remove(K key)
    {
        try
        {
            _cache.remove(key);
            trackRemove();
        }
        catch (IllegalStateException ise)
        {
            if (ViewServlet.isShuttingDown())
                return; // ignore
            throw ise;
        }
    }


    @Override
    public int removeUsingFilter(Filter<K> kFilter)
    {
        return trackRemoves(_cache.removeUsingFilter(kFilter));
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
        trackClear();
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
    public void close()
    {
        _cache.close();
    }


    @Override
    public String getDebugName()
    {
        return _debugName;
    }

    @Override
    public Stats getStats()
    {
        return _stats;
    }

    @Override
    public Stats getTransactionStats()
    {
        return _transactionStats;
    }

    @Override
    public StackTraceElement[] getCreationStackTrace()
    {
        return _stackTrace;
    }

    @Override
    public CacheType getCacheType()
    {
        return _cache.getCacheType();
    }

    @Override
    public TrackingCache getTrackingCache()
    {
        return this;
    }

    @Override
    public String toString()
    {
        return getDebugName();
    }

    private V trackGet(V value)
    {
        _stats.gets.incrementAndGet();

        if (value == null)
            _stats.misses.incrementAndGet();

        return value;
    }

    private void trackPut(V value)
    {
        assert null != value : "Attempt to cache null into " + getDebugName() + "; must use marker for null instead.";
        _stats.puts.incrementAndGet();

        long maxSize = _stats.max_size.get();
        long currentSize = size();
        if (currentSize > maxSize)
            _stats.max_size.compareAndSet(maxSize, currentSize);
    }

    private void trackExpiration()
    {
        _stats.expirations.incrementAndGet();
    }

    private void trackRemove()
    {
        _stats.removes.incrementAndGet();
    }

    private int trackRemoves(int removes)
    {
        _stats.removes.addAndGet(removes);
        return removes;
    }

    private void trackClear()
    {
        _stats.clears.incrementAndGet();
    }


    public SimpleCache getWrappedCache()
    {
        return _cache;
    }

    /* CacheMBean */

    @Override
    public int getSize()
    {
        return size();
    }

    @Override
    public CacheStats getCacheStats()
    {
        return CacheManager.getCacheStats(this);
    }

    public DynamicMBean createDynamicMBean()
    {
        return new StandardMBean(this, CacheMXBean.class, true);
    }
}
