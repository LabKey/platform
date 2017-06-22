/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
 * Date: Jul 8, 2010
 * Time: 9:32:10 AM
 */

public interface Cache<K, V>
{
    void put(K key, V value);

    void put(K key, V value, long timeToLive);

    V get(K key);

    /**
     * The wrapped calls to get() and put() are not guaranteed synchronous (see subclass/wrapper impl)
     */
    V get(K key, @Nullable Object arg, CacheLoader<K,V> loader);

    void remove(K key);

    /** Removes every element in the cache where filter.accept(K key) evaluates to true.
     * Returns the number of elements that were removed.
     */
    int removeUsingFilter(Filter<K> filter);

    Set<K> getKeys();

    void clear();

    /**
     * Some CacheProviders (e.g., Ehcache) hold on to the caches they create.  close() lets us discard temporary
     * caches when we're done with them (e.g., after a transaction is complete) so we don't leak them.
     */
    void close();

    // Get the underlying implementation cache
    TrackingCache getTrackingCache();
}
