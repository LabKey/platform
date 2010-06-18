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
package org.labkey.api.collections;

import org.apache.log4j.Logger;

/**
 * synchronized cache implemented using TTLCacheMap
 * like DatabaseCache, but not transaction aware 
 */
public class Cache
{
    private static Logger _log = Logger.getLogger(Cache.class);

    public static final long SECOND = TTLCacheMap.SECOND;
    public static final long MINUTE = TTLCacheMap.MINUTE;
    public static final long HOUR = TTLCacheMap.HOUR;
    public static final long DAY = TTLCacheMap.DAY;

    public static long DEFAULT_TIMEOUT = HOUR;
    private static int CACHE_SIZE = 10000;

    private static final Cache _instance = new Cache(CACHE_SIZE, DEFAULT_TIMEOUT, "sharedCache");

    public static Cache getShared()
    {
        return _instance;
    }

    
    private final TTLCacheMap<String, Object> _cache;

    public Cache(int size, long defaultTimeToLive, String debugName)
    {
        _cache = new TTLCacheMap<String, Object>(size, defaultTimeToLive, debugName); 
    }


    public synchronized void put(String name, Object obj)
    {
        _cache.put(name, obj);
    }


    public synchronized void put(String name, Object obj, long msToLive)
    {
        _logDebug("Cache.put(" + name + ")");
        _cache.put(name, obj, msToLive);
    }


    public synchronized Object get(String name)
    {
        Object o =  _cache.get(name);
        _logDebug("Cache.get(" + name + ") " + (null==o ? " not found" : "found"));
        return o;
    }


    public synchronized void remove(String name)
    {
        _logDebug("Cache.remove(" + name + ")");
        _cache.remove(name);
    }


    public synchronized void removeUsingPrefix(String name)
    {
        _logDebug("Cache.removeUsingPrefix(" + name + ")");
        _cache.removeUsingPrefix(name);
    }


    public synchronized void clear()
    {
        _cache.clear();
    }


    private void _logDebug(String msg)
    {
//        _log.debug(msg);
    }
}
