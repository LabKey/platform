package org.labkey.api.cache;

import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 9:32:10 AM
 */

// Cache providers return caches that implement this interface
public interface BasicCache<K, V>
{
    // Returns previous value or null
    V put(K key, V value);

    // Returns previous value or null
    V put(K key, V value, long timeToLive);

    V get(K key);

    V remove(K key);

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
