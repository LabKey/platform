package org.labkey.api.collections;

import java.util.HashMap;
import java.util.Map;

/** Enforce that get(K) is always called with Long. */
public class LongHashMap<V> extends HashMap<Long, V>
{
    public LongHashMap()
    {
        super();
    }

    public LongHashMap(int size)
    {
        super(size);
    }

    public LongHashMap(Map<Long, V> map)
    {
        super(map);
    }

    private Long _long(Object key)
    {
        if (null != key && key.getClass() != Long.class)
            throw new IllegalStateException();
        return (Long)key;
    }

    @Override
    public V get(Object key)
    {
        return super.get(_long(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        return super.getOrDefault(_long(key), defaultValue);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return super.containsKey(_long(key));
    }

    @Override
    public V remove(Object key)
    {
        return super.remove(_long(key));
    }
}
