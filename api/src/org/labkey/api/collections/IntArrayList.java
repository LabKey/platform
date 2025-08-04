package org.labkey.api.collections;

import java.util.ArrayList;

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
}
