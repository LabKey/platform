/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.collections.TTLCacheMap;

/*
* User: adam
* Date: Nov 9, 2009
* Time: 10:49:04 PM
*/
public class TransactionCacheMap<K, V> extends TTLCacheMap<K, V>
{
    private boolean _hasWritten = false;
    private final TTLCacheMap<K, V> _sharedCacheMap;

    // A read-through transaction cache.  Reads through to the passed-in shared cache map until any write occurs, at which
    // point it switches to using a private cache map for the remainder of the transaction.
    public TransactionCacheMap(TTLCacheMap<K, V> sharedCacheMap)
    {
        super(sharedCacheMap.getMaxSize(), sharedCacheMap.getDefaultExpires(), "transaction cache: " + sharedCacheMap.getDebugName());
        _sharedCacheMap = sharedCacheMap;
        stats = _sharedCacheMap.transactionStats;      // All stats accumulate to the shared cache's transaction stats
    }

    @Override
    protected void addToKnownCacheMaps()
    {
        // Transaction caches are transient & short-lived -- don't hold onto them
    }

    @Override
    public V get(Object key)
    {
        if (_hasWritten)
            return super.get(key);
        else
            return _sharedCacheMap.get(key);
    }

    @Override
    public V put(K key, V value)
    {
        _hasWritten = true;
        return super.put(key, value);
    }

    @Override
    public V put(K key, V value, long timeToLive)
    {
        _hasWritten = true;
        return super.put(key, value, timeToLive);
    }

    @Override
    public V remove(Object key)
    {
        _hasWritten = true;
        return super.remove(key);
    }

    @Override
    public void removeUsingPrefix(String prefix)
    {
        _hasWritten = true;
        super.removeUsingPrefix(prefix);
    }

    @Override
    public void clear()
    {
        _hasWritten = true;
        super.clear();
    }
}
