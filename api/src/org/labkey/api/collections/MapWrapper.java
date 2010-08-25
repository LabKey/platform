package org.labkey.api.collections;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Aug 25, 2010
 * Time: 10:05:30 AM
 */
public abstract class MapWrapper<K, V> implements Map<K, V>
{
    protected final Map<K, V> _map;

    public MapWrapper(Map<K, V> map)
    {
        _map = map;
    }

    @Override
    public int size()
    {
        return _map.size();
    }

    @Override
    public boolean isEmpty()
    {
        return _map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return _map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return _map.containsValue(value);
    }

    @Override
    public V get(Object key)
    {
        return _map.get(key);
    }

    public V put(K key, V value)
    {
        return _map.put(key, value);
    }

    @Override
    public V remove(Object key)
    {
        return _map.remove(key);
    }

    public void putAll(Map<? extends K, ? extends V> m)
    {
        _map.putAll(m);
    }

    @Override
    public void clear()
    {
        _map.clear();
    }

    @Override
    public Set<K> keySet()
    {
        return _map.keySet();
    }

    @Override
    public Collection<V> values()
    {
        return _map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet()
    {
        return _map.entrySet();
    }

    @Override
    public boolean equals(Object o)
    {
        return _map.equals(o);
    }

    @Override
    public int hashCode()
    {
        return _map.hashCode();
    }

    @Override
    public String toString()
    {
        return _map.toString();
    }
}
