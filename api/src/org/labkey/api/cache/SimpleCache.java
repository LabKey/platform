package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

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

    /** Removes every element in the cache where filter.accept(K key) evaluates to true.
     * Returns the number of elements that were removed.
     */
    int removeUsingFilter(Filter<K> filter);

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
}
