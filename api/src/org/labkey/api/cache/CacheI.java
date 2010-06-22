package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 10:07:01 AM
 */

// TODO: Rename to Cache after big commit
public interface CacheI<K, V>
{
    V put(K key, V value);

    V put(K key, V value, long timeToLive);

    V get(K key);

    V remove(K key);

    void clear();
}
