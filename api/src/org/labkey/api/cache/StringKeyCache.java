package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 11:28:36 AM
 */
public interface StringKeyCache<V> extends CacheI<String, V>
{
    void removeUsingPrefix(String prefix);
}
