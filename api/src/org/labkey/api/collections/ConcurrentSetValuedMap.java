package org.labkey.api.collections;

import org.apache.commons.collections4.multimap.AbstractSetValuedMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// A concurrent multivalued map that's appropriate when each key is mapped to a small number of values
public class ConcurrentSetValuedMap<K, V> extends AbstractSetValuedMap<K, V>
{
    public ConcurrentSetValuedMap()
    {
        super(new ConcurrentHashMap<>());
    }

    @Override
    protected Set<V> createCollection()
    {
        // Be sure to review all usages of ConcurrentSetValuedMap before changing this implementation. Callers
        // currently expect the behavior of synchronizedSet().
        return Collections.synchronizedSet(new HashSet<>(5));
    }
}
