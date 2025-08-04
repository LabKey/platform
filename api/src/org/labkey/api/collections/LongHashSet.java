package org.labkey.api.collections;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforce that get(K) is always called with Long. */
public class LongHashSet extends HashSet<Long>
{
    public LongHashSet()
    {
        super();
    }

    public LongHashSet(int size)
    {
        super(size);
    }

    public LongHashSet(Set<Long> set)
    {
        super(set);
    }

    public LongHashSet(Collection<Long> col)
    {
        super(col);
    }

    private Long _long(Object key)
    {
        if (null != key && key.getClass() != Long.class)
            throw new IllegalStateException();
        return (Long)key;
    }

    @Override
    public boolean contains(Object o)
    {
        return super.contains(_long(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_long(o));
    }
}
