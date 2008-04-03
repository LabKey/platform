package org.labkey.api.data.collections;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collection;

public class CaseInsensitiveMap<V> implements Map<String, V>
{
    Map<String, V> _map;
    Map<String, String> _correctCaseMap;

    public CaseInsensitiveMap(Map<String, V> map)
    {
        _map = map;
        _correctCaseMap = new HashMap();
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
        assert !_correctCaseMap.containsKey(key.toLowerCase());
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
