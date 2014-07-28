package org.labkey.api.collections;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

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
    // Collection or Map that is not a known immutable type; otherwise returns null. Currently recognizes only the
    // standard java.util.Collections unmodifiable, empty, and singleton types as immutable.
    // TODO: Extend to recognize apache collections Unmodifiable interface and/or guava ImmutableCollection
    public static @Nullable String getModifiableCollectionMapOrArrayType(@Nullable Object value)
    {
        if (null == value)
            return null;

        if (value instanceof Collection)
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
            assertUnmodifiable(Collections.emptyNavigableSet());
            assertUnmodifiable(Collections.emptySortedSet());
            assertUnmodifiable(Collections.singleton(null));
            assertUnmodifiable(Collections.unmodifiableSet(new HashSet<>()));
            assertUnmodifiable(Collections.unmodifiableSortedSet(new TreeSet<>()));
            assertUnmodifiable(Collections.unmodifiableNavigableSet(new TreeSet<>()));

            // Unmodifiable Maps
            assertUnmodifiable(Collections.emptyMap());
            assertUnmodifiable(Collections.emptyNavigableMap());
            assertUnmodifiable(Collections.emptySortedMap());
            assertUnmodifiable(Collections.singletonMap(null, null));
            assertUnmodifiable(Collections.unmodifiableMap(new HashMap<>()));
            assertUnmodifiable(Collections.unmodifiableSortedMap(new TreeMap<>()));
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
