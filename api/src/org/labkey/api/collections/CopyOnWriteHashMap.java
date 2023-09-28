package org.labkey.api.collections;

import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe version of {@link HashMap} in which all operations that change the Map are implemented by making
 * a new copy of the underlying Map. This is appropriate for scenarios where reads far outnumber writes.
 */
public class CopyOnWriteHashMap<K, V> extends CopyOnWriteMap<K, V, HashMap<K, V>>
{
    public CopyOnWriteHashMap()
    {
    }

    public CopyOnWriteHashMap(int initialCapacity)
    {
        super(initialCapacity);
    }

    public CopyOnWriteHashMap(Map<K, V> data)
    {
        super(data);
    }

    @Override
    protected HashMap<K, V> newMap()
    {
        return new HashMap<>();
    }

    @Override
    protected HashMap<K, V> newMap(int initialCapacity)
    {
        return new HashMap<>(initialCapacity);
    }

    @Override
    protected HashMap<K, V> newMap(Map<K, V> data)
    {
        return new HashMap<>(data);
    }
}
