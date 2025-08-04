package org.labkey.api.collections;

import java.util.ArrayList;

public class LongArrayList extends ArrayList<Long>
{
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
}
