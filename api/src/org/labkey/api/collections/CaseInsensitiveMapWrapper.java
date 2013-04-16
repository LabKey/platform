/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import java.util.HashMap;
import java.util.Map;

public class CaseInsensitiveMapWrapper<V> extends MapWrapper<String, V> implements Serializable
{
    private final Map<String, String> _correctCaseMap;

    public CaseInsensitiveMapWrapper(Map<String, V> map)
    {
        super(map);
        _correctCaseMap = new HashMap<>();
        for (Map.Entry<? extends String, ? extends V> entry : map.entrySet())
        {
            _correctCaseMap.put(entry.getKey().toLowerCase(), entry.getKey());
        }
    }

    public CaseInsensitiveMapWrapper(Map<String, V> map, CaseInsensitiveMapWrapper<V> caseMapping)
    {
        super(map);
        _correctCaseMap = caseMapping._correctCaseMap;
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
        _correctCaseMap.put(null==key?null:key.toLowerCase(), key);
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

    public void putAll(Map<? extends String, ? extends V> t)
    {
        for (Map.Entry<? extends String, ? extends V> entry : t.entrySet())
        {
            put(entry.getKey(), entry.getValue());
        }
    }
}
