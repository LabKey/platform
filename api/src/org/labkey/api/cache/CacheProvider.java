package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:39:13 AM
 */
public interface CacheProvider
{
    <K, V> BasicCache<K, V> getBasicCache(String debugName, int limit, long defaultTimeToLive);
}
