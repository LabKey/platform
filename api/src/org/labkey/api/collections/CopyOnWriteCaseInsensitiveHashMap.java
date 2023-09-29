package org.labkey.api.collections;

import java.util.Map;

/**
 * A thread-safe version of {@link CaseInsensitiveHashMap} in which all operations that change the Map are implemented
 * by making a new copy of the underlying Map. This is appropriate for scenarios where reads vastly outnumber writes.
 */
public class CopyOnWriteCaseInsensitiveHashMap<V> extends CopyOnWriteMap<String, V, CaseInsensitiveHashMap<V>>
{
    public CopyOnWriteCaseInsensitiveHashMap()
    {
    }

    public CopyOnWriteCaseInsensitiveHashMap(int initialCapacity)
    {
        super(initialCapacity);
    }

    public CopyOnWriteCaseInsensitiveHashMap(Map<String, V> data)
    {
        super(data);
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap()
    {
        return new CaseInsensitiveHashMap<>();
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap(int initialCapacity)
    {
        return new CaseInsensitiveHashMap<>(initialCapacity);
    }

    @Override
    protected CaseInsensitiveHashMap<V> newMap(Map<String, V> data)
    {
        return new CaseInsensitiveHashMap<>(data);
    }
}
