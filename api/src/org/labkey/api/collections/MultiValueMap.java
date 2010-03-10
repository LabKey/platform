package org.labkey.api.collections;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Mar 9, 2010
 * Time: 5:20:32 PM
 */

// Lame version of commons collections MultiValueMap that adds generics.  Unfortunately, collections-generic doesn't
// include this.
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
