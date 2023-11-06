/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.labkey.api.collections;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A thread-safe version of {@link Map} in which all operations that change the
 * Map are implemented by making a new copy of the underlying Map.
 * While the creation of a new Map can be expensive, this class is designed for
 * cases in which the primary function is to read data from the Map, not to
 * modify the Map. Therefore, the operations that do not cause a change to the
 * Map happen quickly and concurrently.
 *
 * @param <K> The key type
 * @param <V> The value type
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * <a href="https://github.com/apache/mina/blob/2.2.X/mina-core/src/main/java/org/apache/mina/util/CopyOnWriteMap.java">Original Source</a>
 * Original Mina source was modified to allow for different internal map implementations (e.g., HashMap, CaseInsensitiveHashMap, etc.).
 */
abstract class CopyOnWriteMap<K, V, MAP extends Map<K, V>> implements Map<K, V>, Cloneable {
    private volatile MAP internalMap;

    /**
     * Creates a new instance of CopyOnWriteMap.
     */
    public CopyOnWriteMap() {
        internalMap = newMap();
    }

    /**
     * Creates a new instance of CopyOnWriteMap with the specified initial size
     *
     * @param initialCapacity The initial size of the Map.
     */
    public CopyOnWriteMap(int initialCapacity) {
        internalMap = newMap(initialCapacity);
    }

    /**
     * Creates a new instance of CopyOnWriteMap in which the
     * initial data being held by this map is contained in
     * the supplied map.
     *
     * @param data A Map containing the initial contents to be placed into
     *  this class.
     */
    public CopyOnWriteMap(Map<K, V> data) {
        internalMap = newMap(data);
    }

    protected abstract MAP newMap();
    protected abstract MAP newMap(int initialCapacity);
    protected abstract MAP newMap(Map<K, V> data);

    /**
     * Adds the provided key and value to this map.
     *
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public V put(K key, V value) {
        synchronized (this) {
            MAP newMap = newMap(internalMap);
            V val = newMap.put(key, value);
            internalMap = newMap;

            return val;
        }
    }

    /**
     * Removes the value and key from this map based on the
     * provided key.
     *
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    public V remove(Object key) {
        synchronized (this) {
            MAP newMap = newMap(internalMap);
            V val = newMap.remove(key);
            internalMap = newMap;

            return val;
        }
    }

    /**
     * Inserts all the keys and values contained in the
     * provided map to this map.
     *
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> newData) {
        synchronized (this) {
            MAP newMap = newMap(internalMap);
            newMap.putAll(newData);
            internalMap = newMap;
        }
    }

    /**
     * Removes all entries in this map.
     *
     * @see java.util.Map#clear()
     */
    @Override
    public void clear() {
        synchronized (this) {
            internalMap = newMap();
        }
    }

    @Override
    public V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mappingFunction)
    {
        synchronized (this) {
            return Map.super.computeIfAbsent(key, mappingFunction);
        }
    }

    // ==============================================
    // ==== Below are methods that do not modify ====
    // ====         the internal Maps            ====
    // ==============================================
    /**
     * @return the number of key/value pairs in this map.
     *
     * @see java.util.Map#size()
     */
    @Override
    public int size() {
        return internalMap.size();
    }

    /**
     * @return true if this map is empty, otherwise false.
     *
     * @see java.util.Map#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    /**
     * @return true if this map contains the provided key, otherwise
     * this method return false.
     *
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(Object key) {
        return internalMap.containsKey(key);
    }

    /**
     * @return true if this map contains the provided value, otherwise
     * this method returns false.
     *
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    @Override
    public boolean containsValue(Object value) {
        return internalMap.containsValue(value);
    }

    /**
     * @return the value associated with the provided key from this
     * map.
     *
     * @see java.util.Map#get(java.lang.Object)
     */
    @Override
    public V get(Object key) {
        return internalMap.get(key);
    }

    /**
     * This method will return a read-only {@link Set}.
     */
    @Override
    public @NotNull Set<K> keySet() {
        return internalMap.keySet();
    }

    /**
     * This method will return a read-only {@link Collection}.
     */
    @Override
    public @NotNull Collection<V> values() {
        return internalMap.values();
    }

    /**
     * This method will return a read-only {@link Set}.
     */
    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        return internalMap.entrySet();
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
