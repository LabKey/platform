package org.labkey.api.cache;

import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:07:01 AM
 */

public interface Cache<K, V>
{
    V put(K key, V value);

    V put(K key, V value, long timeToLive);

    V get(K key);

    V remove(K key);

    void removeUsingFilter(Filter<K> filter);

    void clear();

    int getMaxSize();

    long getDefaultExpires();

    String getDebugName();

    Stats getTransactionStats();

    int size();
}
