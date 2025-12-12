package org.labkey.api.collections;

import org.apache.commons.collections4.multimap.AbstractListValuedMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public class ArrayListValuedTreeMap<K, V> extends AbstractListValuedMap<K, V>
{
    public ArrayListValuedTreeMap(Comparator<? super K> comparator)
    {
        super(new TreeMap<>(comparator));
    }

    @Override
    protected List<V> createCollection()
    {
        return new ArrayList<>();
    }
}
