/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import org.apache.log4j.Logger;

/**
 * synchronized cache implemented using TTLCacheMap
 * like DatabaseCache, but not transaction aware
 */
public class CacheImpl<K, V> implements CacheI<K, V>
{
    private static Logger _log = Logger.getLogger(CacheImpl.class);

    private final TTLCacheMap<K, V> _cache;

    public CacheImpl(int size, long defaultTimeToLive, String debugName)
    {
        _cache = new TTLCacheMap<K, V>(size, defaultTimeToLive, debugName);
    }


    public synchronized V put(K key, V value)
    {
        _logDebug("Cache.put(" + key + ")");
        return _cache.put(key, value);
    }


    public synchronized V put(K key, V value, long msToLive)
    {
        _logDebug("Cache.put(" + key + ")");
        return _cache.put(key, value, msToLive);
    }


    public synchronized V get(K key)
    {
        V v = _cache.get(key);
        _logDebug("Cache.get(" + key + ") " + (null == v ? " not found" : "found"));
        return v;
    }


    public synchronized V remove(K key)
    {
        _logDebug("Cache.remove(" + key + ")");
        return _cache.remove(key);
    }


    public synchronized void removeUsingPrefix(String key)
    {
        _logDebug("Cache.removeUsingPrefix(" + key + ")");
        _cache.removeUsingPrefix(key);
    }


    public synchronized void clear()
    {
        _cache.clear();
    }


    private void _logDebug(String msg)
    {
        _log.debug(msg);
    }
}