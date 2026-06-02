/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A lightweight Map proxy that tracks structural modifications (additions and removals)
 * without requiring deep copies or full iteration of the underlying map.
 * <p>
 * This class is highly optimized for "happy path" data-processing scenarios where structural
 * changes are rare. Tracking sets are allocated lazily only upon the first actual addition
 * or removal. Standard value updates to existing keys pass through with zero allocation overhead.
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * Map<String, Object> baseRow = new CaseInsensitiveHashMap<>();
 * baseRow.put("ColumnA", "Value1");
 *
 * DeltaTrackingMap<Object> trackedRow = DeltaTrackingMap.wrap(baseRow);
 *
 * // Updating an existing key (zero tracking overhead)
 * trackedRow.put("ColumnA", "NewValue");
 *
 * // Adding a new key
 * trackedRow.put("ColumnB", "Value2");
 *
 * // Removing a key
 * trackedRow.remove("ColumnA");
 *
 * if (trackedRow.hasStructuralChanges())
 * {
 *     Set<String> added = trackedRow.getAddedKeys();     // Contains ["ColumnB"]
 *     Set<String> removed = trackedRow.getRemovedKeys(); // Contains ["ColumnA"]
 *
 *     // Reset tracking state if you need to pass the map to another processor
 *     trackedRow.resetTracking();
 * }
 * }</pre>
 *
 * @param <V> the type of mapped values
 */
public class DeltaTrackingMap<V> implements Map<String, V>
{
    private final Map<String, V> delegate;
    private Set<String> added = null;
    private Set<String> removed = null;

    public DeltaTrackingMap(Map<String, V> delegate)
    {
        this.delegate = delegate;
    }

    protected Set<String> newTrackingSet()
    {
        return new HashSet<>();
    }

    @Override
    public V put(String key, V value)
    {
        boolean isNew = !delegate.containsKey(key);
        V prev = delegate.put(key, value);

        if (isNew)
        {
            // If it was previously removed, an add operation cancels out the removal
            if (removed != null && removed.contains(key))
                removed.remove(key);
            else
            {
                if (added == null)
                    added = newTrackingSet();
                added.add(key);
            }
        }

        return prev;
    }

    @Override
    public V remove(Object key)
    {
        boolean exists = delegate.containsKey(key);
        V prev = delegate.remove(key);

        if (exists && key instanceof String strKey)
        {
            // If it was just added, a remove cancels out the add operation
            if (added != null && added.contains(strKey))
                added.remove(strKey);
            else
            {
                if (removed == null)
                    removed = newTrackingSet();
                removed.add(strKey);
            }
        }

        return prev;
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> m)
    {
        for (Entry<? extends String, ? extends V> entry : m.entrySet())
            put(entry.getKey(), entry.getValue());
    }

    @Override
    public void clear()
    {
        // Snapshot keys first to avoid ConcurrentModificationException during removal
        for (String key : new ArrayList<>(delegate.keySet()))
            remove(key);
    }

    // --- State Checking and Reset ---

    public boolean hasStructuralChanges()
    {
        return (added != null && !added.isEmpty()) || (removed != null && !removed.isEmpty());
    }

    public Set<String> getAddedKeys()
    {
        return added != null ? Collections.unmodifiableSet(added) : Collections.emptySet();
    }

    public Set<String> getRemovedKeys()
    {
        return removed != null ? Collections.unmodifiableSet(removed) : Collections.emptySet();
    }

    public void resetTracking()
    {
        if (added != null) added.clear();
        if (removed != null) removed.clear();
    }

    // --- Standard Delegated Methods ---

    @Override
    public int size()
    {
        return delegate.size();
    }

    @Override
    public boolean isEmpty()
    {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key)
    {
        return delegate.get(key);
    }

    /**
     * Returns an unmodifiable view of the key set. Use {@link #put} and {@link #remove} to
     * mutate the map so that structural changes are correctly tracked.
     */
    @Override
    public @NotNull Set<String> keySet()
    {
        return Collections.unmodifiableSet(delegate.keySet());
    }

    /**
     * Returns an unmodifiable view of the values collection. Use {@link #put} and {@link #remove} to
     * mutate the map so that structural changes are correctly tracked.
     */
    @Override
    public @NotNull Collection<V> values()
    {
        return Collections.unmodifiableCollection(delegate.values());
    }

    /**
     * Returns an unmodifiable view of the entry set. Use {@link #put} and {@link #remove} to
     * mutate the map so that structural changes are correctly tracked.
     */
    @Override
    public @NotNull Set<Entry<String, V>> entrySet()
    {
        return Collections.unmodifiableSet(delegate.entrySet());
    }

    /**
     * Returns a {@link CaseInsensitive} wrapper when the delegate implements
     * {@link CaseInsensitiveCollection}, and a plain {@link DeltaTrackingMap} otherwise.
     * Prefer this over calling a constructor directly when the case-sensitivity of the
     * delegate is not known at compile time.
     */
    public static <V> DeltaTrackingMap<V> wrap(Map<String, V> delegate)
    {
        if (delegate instanceof CaseInsensitiveCollection)
            return new DeltaTrackingMap.CaseInsensitive<>(delegate);
        return new DeltaTrackingMap<>(delegate);
    }

    /**
     * A case-insensitive variant of {@link DeltaTrackingMap} that also implements
     * {@link CaseInsensitiveCollection}. Use this when wrapping a case-insensitive delegate such
     * as {@link CaseInsensitiveHashMap} so that downstream code relying on
     * {@code instanceof CaseInsensitiveCollection} continues to work correctly.
     *
     * @param <V> the type of mapped values
     */
    public static class CaseInsensitive<V> extends DeltaTrackingMap<V> implements CaseInsensitiveCollection
    {
        public CaseInsensitive(Map<String, V> delegate)
        {
            super(delegate);
        }

        @Override
        protected Set<String> newTrackingSet()
        {
            return new CaseInsensitiveHashSet();
        }
    }

    public static class TestCase extends Assert
    {
        private static DeltaTrackingMap<String> createTracker()
        {
            Map<String, String> baseMap = new CaseInsensitiveHashMap<>();
            baseMap.put("ExistingKey1", "Value1");
            baseMap.put("ExistingKey2", "Value2");
            baseMap.put("ExistingKey3", "Value3");
            return new DeltaTrackingMap.CaseInsensitive<>(baseMap);
        }

        @Test
        public void testNoStructuralChanges()
        {
            DeltaTrackingMap<String> map = createTracker();

            // Updating an existing key should not trigger structural changes
            map.put("ExistingKey1", "NewValue1");
            // Case-insensitive update of an existing key
            map.put("existingkey2", "NewValue2");

            assertFalse("Updates to existing keys should not flag structural changes", map.hasStructuralChanges());
            assertTrue(map.getAddedKeys().isEmpty());
            assertTrue(map.getRemovedKeys().isEmpty());
        }

        @Test
        public void testAdditions()
        {
            DeltaTrackingMap<String> map = createTracker();

            map.put("NewKey1", "NewValue");
            assertTrue("Adding a key should flag structural changes", map.hasStructuralChanges());
            assertTrue(map.getAddedKeys().contains("NewKey1"));
            assertTrue(map.getRemovedKeys().isEmpty());

            // Case-insensitive check of the tracking set
            assertTrue(map.getAddedKeys().contains("newkey1"));
        }

        @Test
        public void testRemovals()
        {
            DeltaTrackingMap<String> map = createTracker();

            map.remove("ExistingKey1");
            assertTrue("Removing a key should flag structural changes", map.hasStructuralChanges());
            assertTrue(map.getRemovedKeys().contains("ExistingKey1"));
            assertTrue(map.getAddedKeys().isEmpty());

            // Case-insensitive remove and check
            map.remove("existingkey2");
            assertTrue(map.getRemovedKeys().contains("ExistingKey2"));
            assertEquals(2, map.getRemovedKeys().size());
        }

        @Test
        public void testCancellations()
        {
            DeltaTrackingMap<String> map = createTracker();

            // Remove then Add (Cancels the removal)
            map.remove("ExistingKey1");
            assertTrue(map.getRemovedKeys().contains("ExistingKey1"));

            map.put("ExistingKey1", "RestoredValue");
            assertFalse("Adding back a removed key should cancel the structural change", map.hasStructuralChanges());
            assertTrue(map.getRemovedKeys().isEmpty());
            assertTrue(map.getAddedKeys().isEmpty());

            // Add then Remove (Cancels the addition)
            map.put("TemporaryKey", "TempValue");
            assertTrue(map.getAddedKeys().contains("TemporaryKey"));

            map.remove("TemporaryKey");
            assertFalse("Removing a newly added key should cancel the structural change", map.hasStructuralChanges());
            assertTrue(map.getRemovedKeys().isEmpty());
            assertTrue(map.getAddedKeys().isEmpty());
        }

        @Test
        public void testBulkOperations()
        {
            DeltaTrackingMap<String> map = createTracker();

            // test putAll()
            Map<String, String> bulkAdds = new CaseInsensitiveHashMap<>();
            bulkAdds.put("ExistingKey1", "UpdatedValue"); // Update
            bulkAdds.put("NewBulk1", "V1");               // Add
            bulkAdds.put("NewBulk2", "V2");               // Add

            map.putAll(bulkAdds);
            assertTrue(map.hasStructuralChanges());
            assertEquals("Should only track the 2 new keys", 2, map.getAddedKeys().size());
            assertTrue(map.getAddedKeys().contains("NewBulk1"));
            assertTrue(map.getAddedKeys().contains("NewBulk2"));

            map.resetTracking();

            // test clear()
            map.clear();
            assertTrue(map.hasStructuralChanges());
            assertEquals("Should track all keys removed during clear", 5, map.getRemovedKeys().size());
            assertTrue(map.isEmpty());
        }

        @Test
        public void testResetTracking()
        {
            DeltaTrackingMap<String> map = createTracker();

            map.put("NewKey", "Val");
            map.remove("ExistingKey1");

            assertTrue(map.hasStructuralChanges());
            assertEquals(1, map.getAddedKeys().size());
            assertEquals(1, map.getRemovedKeys().size());

            map.resetTracking();

            assertFalse(map.hasStructuralChanges());
            assertTrue(map.getAddedKeys().isEmpty());
            assertTrue(map.getRemovedKeys().isEmpty());
        }

        @Test
        public void testEncapsulationOfTrackingSets()
        {
            DeltaTrackingMap<String> map = createTracker();
            map.put("NewKey", "Val");

            Set<String> addedKeys = map.getAddedKeys();
            try
            {
                addedKeys.remove("NewKey");
                fail("getAddedKeys() should return an unmodifiable set to prevent internal state corruption.");
            }
            catch (UnsupportedOperationException e)
            {
                // Expected behavior
            }
        }

        @Test
        public void testMapViewsAreUnmodifiable()
        {
            DeltaTrackingMap<String> map = createTracker();

            // keySet().remove() must not bypass tracking
            try
            {
                map.keySet().remove("ExistingKey1");
                fail("keySet() should return an unmodifiable view.");
            }
            catch (UnsupportedOperationException e)
            {
                // Expected behavior
            }
            assertFalse("keySet().remove() must not have modified the map", map.hasStructuralChanges());
            assertTrue(map.containsKey("ExistingKey1"));

            // entrySet() iterator remove() must not bypass tracking
            try
            {
                map.entrySet().iterator().remove();
                fail("entrySet() should return an unmodifiable view.");
            }
            catch (UnsupportedOperationException e)
            {
                // Expected behavior
            }

            // values() remove() must not bypass tracking
            try
            {
                map.values().remove("Value1");
                fail("values() should return an unmodifiable view.");
            }
            catch (UnsupportedOperationException e)
            {
                // Expected behavior
            }
            assertFalse("No mutations should have been tracked", map.hasStructuralChanges());
        }

        @Test
        public void testCrossCaseCancellations()
        {
            DeltaTrackingMap<String> map = createTracker();

            // 1. Remove Exact, Add Lowercase (Cancels the removal)
            map.remove("ExistingKey1");
            map.put("existingkey1", "RestoredValue"); // Different casing than original

            assertFalse("Adding back a removed key with different casing should cancel the structural change", map.hasStructuralChanges());
            assertTrue(map.getRemovedKeys().isEmpty());
            assertTrue(map.getAddedKeys().isEmpty());

            // 2. Add Exact, Remove Lowercase (Cancels the addition)
            map.put("TemporaryKey", "TempValue");
            map.remove("temporarykey"); // Different casing than addition

            assertFalse("Removing a newly added key with different casing should cancel the structural change", map.hasStructuralChanges());
            assertTrue(map.getRemovedKeys().isEmpty());
            assertTrue(map.getAddedKeys().isEmpty());
        }

        @Test
        public void testNullValues()
        {
            DeltaTrackingMap<String> map = createTracker();

            // Adding a new key with a null value
            map.put("NullValueKey", null);
            assertTrue(map.hasStructuralChanges());
            assertTrue(map.getAddedKeys().contains("NullValueKey"));

            // Updating an existing key to null
            map.put("ExistingKey1", null);
            assertEquals("Updating existing key to null should not trigger structural changes", 1, map.getAddedKeys().size());
            assertTrue(map.getRemovedKeys().isEmpty());
        }

        @Test
        public void testNonStringRemoval()
        {
            DeltaTrackingMap<String> map = createTracker();

            // Verify passing a non-String doesn't throw a ClassCastException.
            String result = map.remove(12345);
            assertNull(result);
            assertFalse("Removing a non-existent, non-string key should do nothing", map.hasStructuralChanges());
        }

        @Test
        public void testCaseSensitiveDelegateTracking()
        {
            // A case-sensitive delegate must use case-sensitive tracking sets so that
            // differently cased variants of a key are treated as independent entries.
            Map<String, String> baseMap = new LinkedHashMap<>();
            baseMap.put("Key", "Value");
            DeltaTrackingMap<String> map = new DeltaTrackingMap<>(baseMap);

            // "key" is a brand-new key in a case-sensitive map — must be tracked as an addition
            map.put("key", "lowerValue");
            assertTrue(map.hasStructuralChanges());
            assertEquals(1, map.getAddedKeys().size());
            assertTrue("Added 'key'", map.getAddedKeys().contains("key"));
            assertFalse("'Key' was not added", map.getAddedKeys().contains("Key"));

            // Removing "Key" (original casing) must NOT cancel the tracking of the added "key"
            map.remove("Key");
            assertEquals("'key' (added) and 'Key' (removed) are independent", 1, map.getAddedKeys().size());
            assertTrue(map.getAddedKeys().contains("key"));
            assertEquals(1, map.getRemovedKeys().size());
            assertTrue(map.getRemovedKeys().contains("Key"));
            assertFalse("'key' was not removed", map.getRemovedKeys().contains("key"));
        }

        @Test
        public void testEntrySetValueUpdate()
        {
            DeltaTrackingMap<String> map = createTracker();

            // Get the entry for "ExistingKey1" and update its value directly
            Map.Entry<String, String> found = null;
            for (Map.Entry<String, String> e : map.entrySet())
            {
                if ("ExistingKey1".equals(e.getKey()))
                {
                    found = e;
                    break;
                }
            }
            assertNotNull(found);

            String oldValue = found.setValue("UpdatedViaEntry");
            assertEquals("Value1", oldValue);
            assertEquals("UpdatedViaEntry", map.get("ExistingKey1"));

            // entry.setValue() is a value-only mutation; no key was added or removed
            assertFalse("entry.setValue() on an existing key must not flag structural changes", map.hasStructuralChanges());
            assertTrue(map.getAddedKeys().isEmpty());
            assertTrue(map.getRemovedKeys().isEmpty());
        }

        @Test
        public void testWithLinkedHashMap()
        {
            // Use a standard, case-sensitive map which preserves insertion order
            Map<String, String> baseMap = new LinkedHashMap<>();
            baseMap.put("FirstKey", "V1");
            baseMap.put("SecondKey", "V2");

            DeltaTrackingMap<String> map = new DeltaTrackingMap<>(baseMap);

            // Verify standard tracking works normally
            map.put("ThirdKey", "V3");
            map.remove("FirstKey");

            assertTrue(map.hasStructuralChanges());
            assertEquals(1, map.getAddedKeys().size());
            assertTrue(map.getAddedKeys().contains("ThirdKey"));

            assertEquals(1, map.getRemovedKeys().size());
            assertTrue(map.getRemovedKeys().contains("FirstKey"));

            // Verify underlying map features (iteration order) are preserved
            map.put("FourthKey", "V4");

            String[] expectedOrder = {"SecondKey", "ThirdKey", "FourthKey"};
            int i = 0;
            for (String key : map.keySet())
            {
                assertEquals("Iteration order should match LinkedHashMap's insertion order", expectedOrder[i], key);
                i++;
            }

            map.resetTracking();

            map.put("secondkey", "V-lower");

            assertEquals("LinkedHashMap treats different casing as distinct keys", 4, map.size());
            assertTrue("Tracker flagged the new key", map.getAddedKeys().contains("secondkey"));

            map.remove("SecondKey");

            assertEquals(3, map.size());
            assertFalse(map.containsKey("SecondKey"));
            assertTrue(map.containsKey("secondkey"));

            // With a case-sensitive delegate, "secondkey" (added) and "SecondKey" (removed)
            // are different keys and must be tracked independently — no cancellation.
            assertTrue("Case-sensitive delegate: add and remove of differently-cased keys must not cancel", map.hasStructuralChanges());
            assertEquals(1, map.getAddedKeys().size());
            assertTrue(map.getAddedKeys().contains("secondkey"));
            assertFalse(map.getAddedKeys().contains("SecondKey"));
            assertEquals(1, map.getRemovedKeys().size());
            assertTrue(map.getRemovedKeys().contains("SecondKey"));
            assertFalse(map.getRemovedKeys().contains("secondkey"));
        }
    }
}