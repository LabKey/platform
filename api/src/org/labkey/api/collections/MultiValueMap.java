/*
 * Copyright (c) 2010 LabKey Corporation
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Mar 9, 2010
 * Time: 5:20:32 PM
 */

// Lame version of commons collections MultiValueMap that adds generics.  Collections-generic includes MultiHashMap,
// but not a MultiMap that wraps another map implementation like this.
public abstract class MultiValueMap<K, V>
{
    private Map<K, Collection<V>> _map;

    public MultiValueMap(Map<K, Collection<V>> map)
    {
        _map = map;
    }

    protected abstract Collection<V> createValueCollection();

    public void put(K key, V value)
    {
        Collection<V> values = _map.get(key);

        if (null == values)
        {
            values = createValueCollection();
            _map.put(key, values);
        }

        values.add(value);
    }

    public Collection<V> get(K key)
    {
        return _map.get(key);
    }

    public Set<K> keySet()
    {
        return _map.keySet();
    }

    public int size()
    {
        return _map.keySet().size();
    }

    public boolean containsKey(K key)
    {
        return _map.containsKey(key);
    }
}
