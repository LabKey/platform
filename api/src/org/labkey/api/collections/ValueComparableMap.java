/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.collections;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A TreeMap that compares by *value* instead of key. Adapted from a post on StackOverflow by "Stephen":
 * http://stackoverflow.com/questions/109383/how-to-sort-a-mapkey-value-on-the-values-in-java
 * User: adam
 * Date: 4/28/2014
 */
public class ValueComparableMap<K extends Comparable<K>, V> extends TreeMap<K, V>
{
    //A map for doing lookups on the keys for comparison so we don't get infinite loops
    private final Map<K, V> _valueMap;

    public ValueComparableMap(final Ordering<? super V> partialValueOrdering)
    {
        this(partialValueOrdering, new HashMap<>());
    }

    private ValueComparableMap(Ordering<? super V> partialValueOrdering, HashMap<K, V> valueMap)
    {
        super(partialValueOrdering // Apply the value ordering...
                .onResultOf(Functions.forMap(valueMap)) // ...on the result of getting the value for the key from the map
                .compound(ValueComparableMap.getOrdering())); // ...and include the keys as a tie-breaker
        _valueMap = valueMap;
    }

    // Hack that can be removed in Java 8
    private static <K2> Ordering<K2> getOrdering()
    {
        return (Ordering<K2>)Ordering.natural();
    }

    public V put(K k, V v)
    {
        if (_valueMap.containsKey(k))
        {
            //remove the key in the sorted set before adding the key again
            remove(k);
        }
        _valueMap.put(k, v); //To get "real" unsorted values for the comparator
        return super.put(k, v); //Put it in value order
    }
}