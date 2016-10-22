package org.labkey.api.collections;

import org.apache.commons.collections4.multimap.AbstractListValuedMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Intends to act like a case-insensitive version of
 * org.apache.commons.collections4.multimap.ArrayListValuedHashMap
 *
 * Created by Nick on 10/1/2016.
 */
public class CaseInsensitiveArrayListValuedMap<V> extends AbstractListValuedMap<String, V>
{
    public CaseInsensitiveArrayListValuedMap()
    {
        super(new CaseInsensitiveHashMap<>());
    }

    @Override
    protected List<V> createCollection()
    {
        return new ArrayList<>();
    }

    /**
     * Trims the capacity of all value collections to their current size.
     */
    public void trimToSize()
    {
        for (Collection<V> coll : getMap().values())
        {
            final ArrayList<V> list = (ArrayList<V>) coll;
            list.trimToSize();
        }
    }
}
