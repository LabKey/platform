/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 9:32:10 AM
 */

// Cache providers return caches that implement this interface.  BasicCache implementations must be thread-safe.
public interface BasicCache<K, V>
{
    // Returns previous value or null
    void put(K key, V value);

    // Returns previous value or null
    void put(K key, V value, long timeToLive);

    V get(K key);

    void remove(K key);

    // Returns the number of elements that were removed
    int removeUsingFilter(Filter<K> filter);

    void clear();

    // Maximum number of elements allowed in the cache
    int getLimit();

    // Current number of elements in the cache
    int size();

    long getDefaultExpires();

    CacheType getCacheType();

    // Some CacheProviders (e.g., Ehcache) hold on to the caches they create.  close() lets us discard temporary
    // caches when we're done with them (e.g., after a transaction is complete) so we don't leak them.
    void close();

    static enum CacheType
    {
        DeterministicLRU,
        NonDeterministicLRU
    }
}
