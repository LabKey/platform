package org.labkey.api.collections;

import java.util.HashMap;
import java.util.function.BiFunction;

/** Enforce that get(K) is always called with Integer. */
public class IntHashMap<V> extends HashMap<Integer, V>
{
    public IntHashMap()
    {
        super();
    }

    public IntHashMap(int size)
    {
        super(size);
    }

    private Integer _int(Object key)
    {
        if (key.getClass() != Integer.class)
            throw new IllegalStateException();
        return (Integer)key;
    }

    @Override
    public V compute(Integer key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction)
    {
        return super.compute(key, remappingFunction);
    }

    @Override
    public V get(Object key)
    {
        return super.get(_int(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        return super.getOrDefault(_int(key), defaultValue);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return super.containsKey(_int(key));
    }
}
