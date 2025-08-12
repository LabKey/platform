package org.labkey.api.collections;

import java.util.ArrayList;
import java.util.Collection;

public class LongArrayList extends ArrayList<Long>
{
    public LongArrayList()
    {}

    public LongArrayList(int capacity)
    {
        super(capacity);
    }

    public LongArrayList(Collection<Long> c)
    {
        super(c);
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
    public int indexOf(Object o)
    {
        return super.indexOf(_long(o));
    }

    @Override
    public int lastIndexOf(Object o)
    {
        return super.lastIndexOf(_long(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_long(o));
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        assert c.stream().allMatch(o -> null == o || o.getClass() == Long.class);
        return super.removeAll(c);
    }
}
