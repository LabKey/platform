package org.labkey.api.collections;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Enforce that get(K) is always called with Long. */
public class IntHashSet extends HashSet<Integer>
{
    public IntHashSet()
    {
        super();
    }

    public IntHashSet(int size)
    {
        super(size);
    }

    public IntHashSet(Set<Integer> set)
    {
        super(set);
    }

    public IntHashSet(Collection<Integer> c)
    {
        super(c);
    }

    private Integer _int(Object key)
    {
        if (null != key && key.getClass() != Integer.class)
            throw new IllegalStateException();
        return (Integer)key;
    }

    @Override
    public boolean contains(Object o)
    {
        return super.contains(_int(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_int(o));
    }
}
