/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collection;

public class CaseInsensitiveMapWrapper<V> implements Map<String, V>, Serializable
{
    Map<String, V> _map;
    Map<String, String> _correctCaseMap;

    public CaseInsensitiveMapWrapper(Map<String, V> map)
    {
        _map = map;
        _correctCaseMap = new HashMap<String, String>();
        for (Map.Entry<? extends String, ? extends V> entry : map.entrySet())
        {
            _correctCaseMap.put(entry.getKey().toLowerCase(), entry.getKey());
        }
    }

    public V get(Object key)
    {
        String correctKey = normalizeKey(key);
        if (correctKey == null)
            return null;
        return _map.get(correctKey);
    }

    protected String normalizeKey(Object key)
    {
        if (!(key instanceof String))
            return null;
        return _correctCaseMap.get(((String) key).toLowerCase());
    }

    public V put(String key, V value)
    {
        String correctKey = normalizeKey(key);
        V ret = null;
        if (correctKey != null)
        {
            ret = remove(correctKey);
        }
        _map.put(key, value);
//        assert !_correctCaseMap.containsKey(key.toLowerCase());
        _correctCaseMap.put(key.toLowerCase(), key);
        return ret;
    }

    public V remove(Object key)
    {
        return _map.remove(normalizeKey(key));
    }

    public boolean containsKey(Object key)
    {
        return _map.containsKey(normalizeKey(key));
    }

    public void clear()
    {
        _map.clear();
        _correctCaseMap.clear();
    }

    public boolean containsValue(Object value)
    {
        return _map.containsValue(value);
    }

    public Set<Entry<String, V>> entrySet()
    {
        return _map.entrySet();
    }

    public boolean isEmpty()
    {
        return _map.isEmpty();
    }

    public Set<String> keySet()
    {
        return _map.keySet();
    }

    public void putAll(Map<? extends String, ? extends V> t)
    {
        for (Map.Entry<? extends String, ? extends V> entry : t.entrySet())
        {
            put(entry.getKey(), entry.getValue());
        }
    }

    public int size()
    {
        return _map.size();
    }

    public Collection<V> values()
    {
        return _map.values();
    }
}
