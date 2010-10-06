package org.labkey.api.cache;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 16, 2010
 * Time: 4:40:24 PM
 */
public interface CacheLoader<K, V>
{
    V load(K key, Object argument);
}
