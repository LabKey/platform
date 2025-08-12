package org.labkey.api.collections;

import java.util.ArrayList;
import java.util.Collection;

public class IntArrayList extends ArrayList<Integer>
{
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
    public int indexOf(Object o)
    {
        return super.indexOf(_int(o));
    }

    @Override
    public int lastIndexOf(Object o)
    {
        return super.lastIndexOf(_int(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_int(o));
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        assert c.stream().allMatch(o -> null == o || o.getClass() == Integer.class);
        return super.removeAll(c);
    }
}
