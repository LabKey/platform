/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

import java.util.Set;

/**
 * User: adam
 * Date: 12/25/11
 * Time: 8:11 PM
 */

// Cache providers return caches that implement this interface, which presents a minimal set of cache operations,
// without support for standard LabKey features such as null markers, cache loaders, statistics, blocking, etc.
// Implementations must be thread-safe.
public interface SimpleCache<K, V>
{
    void put(K key, V value);

    void put(K key, V value, long timeToLive);

    @Nullable V get(K key);

    void remove(K key);

    /**
     * Removes every element in the cache where filter.accept(K key) evaluates to true.
     * Returns the number of elements that were removed.
     */
    int removeUsingFilter(Filter<K> filter);

    Set<K> getKeys();

    void clear();

    /**
     * Maximum number of elements allowed in the cache
     */
    int getLimit();

    // Current number of elements in the cache
    int size();

    // Is this cache empty?
    public boolean isEmpty();

    long getDefaultExpires();

    /**
     * Some CacheProviders (e.g., Ehcache) hold onto the caches they create.  close() lets us discard temporary
     * caches when we're done with them (e.g., after a transaction is complete) so we don't leak them.
     */
    void close();

    CacheType getCacheType();

    void log();
}
