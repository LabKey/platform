package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 11:28:36 AM
 */
public interface StringKeyCache<V> extends Cache<String, V>
{
    int removeUsingPrefix(String prefix);
}
