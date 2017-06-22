/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.Unmodifiable;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.collections4.multimap.UnmodifiableMultiValuedMap;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.PropertyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User: adam
 * Date: 7/27/2014
 * Time: 2:13 PM
 */
public class CollectionUtils
{
    // Collections.Unmodifiable* classes are not public, so grab them statically to use in the methods below
    private static final Class<? extends Collection> UNMODIFIABLE_COLLECTION_CLASS = Collections.unmodifiableCollection(Collections.emptyList()).getClass();
    private static final Class<? extends Map> UNMODIFIABLE_MAP_CLASS = Collections.unmodifiableMap(Collections.emptyMap()).getClass();
    private static final Class<? extends Set> SINGLETON_SET_CLASS = Collections.singleton(null).getClass();
    private static final Class<? extends List> SINGLETON_LIST_CLASS = Collections.singletonList(null).getClass();
    private static final Class<? extends Map> SINGLETON_MAP_CLASS = Collections.singletonMap(null, null).getClass();
    private static final Class<? extends Set> EMPTY_SET_CLASS = Collections.emptySet().getClass();
    private static final Class<? extends List> EMPTY_LIST_CLASS = Collections.emptyList().getClass();
    private static final Class<? extends Map> EMPTY_MAP_CLASS = Collections.emptyMap().getClass();

    // Returns true if value is an Array or value is a Collection or Map that is not a known immutable type; otherwise,
    // returns false. See note below about currently recognized immutable types.
    public static boolean isModifiableCollectionMapOrArray(@Nullable Object value)
    {
        return null != getModifiableCollectionMapOrArrayType(value);
    }

    // Returns a description of value (e.g., "a modifiable set (java.util.HashSet)") if value is an Array or value is a
    // Collection or Map that is not a known immutable type; otherwise returns null. Currently recognizes Apache collections
    // Unmodifiable interface and the standard java.util.Collections unmodifiable, empty, and singleton types as immutable.
    // TODO: Extend to recognize guava ImmutableCollection
    public static @Nullable String getModifiableCollectionMapOrArrayType(@Nullable Object value)
    {
        if (null == value || value instanceof Unmodifiable)
            return null;

        if (value instanceof PropertyManager.PropertyMap)
        {
            if (!((PropertyManager.PropertyMap)value).isLocked())
                return "a modifiable PropertyMap";
        }
        else if (value instanceof Collection)
        {
            if (!UNMODIFIABLE_COLLECTION_CLASS.isInstance(value))
            {
                if (value instanceof Set)
                {
                    if (!EMPTY_SET_CLASS.isInstance(value) && !SINGLETON_SET_CLASS.isInstance(value))
                        return "a modifiable set (" + value.getClass() + ")";
                }
                else if (value instanceof List)
                {
                    if (!EMPTY_LIST_CLASS.isInstance(value) && !SINGLETON_LIST_CLASS.isInstance(value))
                        return "a modifiable list (" + value.getClass() + ")";
                }
                else
                {
                    return "a modifiable collection (" + value.getClass() + ")";
                }
            }
        }
        else if (value instanceof Map)
        {
            if (!UNMODIFIABLE_MAP_CLASS.isInstance(value) && !EMPTY_MAP_CLASS.isInstance(value) && !SINGLETON_MAP_CLASS.isInstance(value))
                return "a modifiable map (" + value.getClass() + ")";
        }
        else if (value.getClass().isArray())
        {
            return "an array";
        }
        else if (value instanceof MultiValuedMap)
        {
            return "a modifiable MultiValuedMap (" + value.getClass() + ")";
        }

        return null;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testModifiableCollectionDetection()
        {
            // Modifiable Lists
            assertModifiable(new ArrayList<>(), "a modifiable list (class java.util.ArrayList)");
            assertModifiable(new LinkedList<>(), "a modifiable list (class java.util.LinkedList)");

            // Modifiable Sets
            assertModifiable(new HashSet<>(), "a modifiable set (class java.util.HashSet)");
            assertModifiable(new LinkedHashSet<>(), "a modifiable set (class java.util.LinkedHashSet)");
            assertModifiable(new TreeSet<>(), "a modifiable set (class java.util.TreeSet)");

            // Modifiable Maps
            assertModifiable(new HashMap<>(), "a modifiable map (class java.util.HashMap)");
            assertModifiable(new LinkedHashMap<>(), "a modifiable map (class java.util.LinkedHashMap)");
            assertModifiable(new TreeMap<>(), "a modifiable map (class java.util.TreeMap)");

            // Modifiable Collection that's neither a List nor a Set
            assertModifiable(new PriorityQueue<>(), "a modifiable collection (class java.util.PriorityQueue)");

            // Arrays
            assertModifiable(new Object[0], "an array");
            assertModifiable(new String[10], "an array");
            assertModifiable(new int[20], "an array");

            // MultiMaps
            assertModifiable(new ArrayListValuedHashMap<String, String>(new HashMap<>()), "a modifiable MultiValuedMap (class org.apache.commons.collections4.multimap.ArrayListValuedHashMap)");
            assertModifiable(new HashSetValuedHashMap<String, String>(new HashMap<>()), "a modifiable MultiValuedMap (class org.apache.commons.collections4.multimap.HashSetValuedHashMap)");

            // null should be "unmodifiable"
            assertUnmodifiable(null);

            // Unmodifiable Collection
            assertUnmodifiable(Collections.unmodifiableCollection(new LinkedHashSet<>()));

            // Unmodifiable Lists
            assertUnmodifiable(Collections.emptyList());
            assertUnmodifiable(Collections.singletonList(null));
            assertUnmodifiable(Collections.unmodifiableList(new ArrayList<>()));

            // Unmodifiable Sets
            assertUnmodifiable(Collections.emptySet());
            assertUnmodifiable(Collections.singleton(null));
            assertUnmodifiable(Collections.unmodifiableSet(new HashSet<>()));
            assertUnmodifiable(Collections.unmodifiableSortedSet(new TreeSet<>()));

            // Unmodifiable Maps
            assertUnmodifiable(Collections.emptyMap());
            assertUnmodifiable(Collections.singletonMap(null, null));
            assertUnmodifiable(Collections.unmodifiableMap(new HashMap<>()));
            assertUnmodifiable(Collections.unmodifiableSortedMap(new TreeMap<>()));

            // Unmodifiable MultiMap
            assertUnmodifiable(UnmodifiableMultiValuedMap.unmodifiableMultiValuedMap(new ArrayListValuedHashMap<>()));
            assertUnmodifiable(MultiMapUtils.unmodifiableMultiValuedMap(new ArrayListValuedHashMap<>()));
            assertUnmodifiable(MultiMapUtils.emptyMultiValuedMap());

            //  These Collections methods are new to Java 8
            assertUnmodifiable(Collections.emptyNavigableSet());
            assertUnmodifiable(Collections.emptySortedSet());
            assertUnmodifiable(Collections.unmodifiableNavigableSet(new TreeSet<>()));
            assertUnmodifiable(Collections.emptyNavigableMap());
            assertUnmodifiable(Collections.emptySortedMap());
            assertUnmodifiable(Collections.unmodifiableNavigableMap(new TreeMap<>()));
        }

        private void assertModifiable(Object value, String expectedType)
        {
            assertTrue(value.getClass() + " should have been flagged as modifiable", isModifiableCollectionMapOrArray(value));
            assertEquals(expectedType, getModifiableCollectionMapOrArrayType(value));
        }

        private void assertUnmodifiable(@Nullable Object value)
        {
            assertFalse((null != value ? value.getClass() : "null") + " should NOT have been flagged as modifiable", isModifiableCollectionMapOrArray(value));
            assertNull(getModifiableCollectionMapOrArrayType(value));
        }
    }
}
