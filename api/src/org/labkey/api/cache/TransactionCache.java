/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.util.Filter;

/*
* User: adam
* Date: Nov 9, 2009
* Time: 10:49:04 PM
*/
public class TransactionCache<V> implements StringKeyCache<V>
{
    private boolean _hasWritten = false;
    private final StringKeyCache<V> _sharedCache;
    private final StringKeyCache<V> _privateCache;

    // A read-through transaction cache.  Reads through to the passed-in shared cache until any write occurs, at which
    // point it switches to using a private cache for the remainder of the transaction.
    public TransactionCache(StringKeyCache<V> sharedCache, StringKeyCache<V> privateCache)
    {
        _privateCache = privateCache;
        _sharedCache = sharedCache;
    }

    @Override
    public V get(String key)
    {
        V v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        return v;
    }


    @Override
    public V get(String key, Object arg, CacheLoader<String, V> loader)
    {
        V v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        if (null == v)
        {
            v = loader.load(key, arg);
            _hasWritten = true;
            _privateCache.put(key, v);
        }

        return v;
    }

    @Override
    public void put(String key, V value)
    {
        _hasWritten = true;
        _privateCache.put(key, value);
    }

    @Override
    public void put(String key, V value, long timeToLive)
    {
        _hasWritten = true;
        _privateCache.put(key, value, timeToLive);
    }

    @Override
    public void remove(String key)
    {
        _hasWritten = true;
        _privateCache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<String> filter)
    {
        _hasWritten = true;
        return _privateCache.removeUsingFilter(filter);
    }

    @Override
    public void clear()
    {
        _hasWritten = true;
        _privateCache.clear();
    }

    @Override
    public int removeUsingPrefix(String prefix)
    {
        _hasWritten = true;
        return _privateCache.removeUsingPrefix(prefix);
    }

    @Override
    public int getLimit()
    {
        return _privateCache.getLimit();
    }

    @Override
    public CacheType getCacheType()
    {
        return _sharedCache.getCacheType();
    }

    @Override
    public void close()
    {
        _privateCache.close();
    }

    @Override
    public int size()
    {
        return _privateCache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _privateCache.getDefaultExpires();
    }
}
