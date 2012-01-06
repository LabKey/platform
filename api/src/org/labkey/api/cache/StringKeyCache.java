package org.labkey.api.cache;

/**
 * User: adam
 * Date: 1/3/12
 * Time: 7:38 PM
 */
public interface StringKeyCache<V> extends Cache<String, V>
{
    int removeUsingPrefix(String prefix);
}
