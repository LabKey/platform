package org.labkey.api.collections;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.function.BiFunction;

/** Enforce that get(K) is always called with Integer. */
public class StringHashMap<V> extends HashMap<String, V>
{
    public StringHashMap()
    {
        super();
    }

    public StringHashMap(int size)
    {
        super(size);
    }

    private String _str(Object key)
    {
        if (null != key && key.getClass() != String.class)
            throw new IllegalStateException();
        return (String)key;
    }

    @Override
    public V get(Object key)
    {
        return super.get(_str(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        return super.getOrDefault(_str(key), defaultValue);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return super.containsKey(_str(key));
    }
}
