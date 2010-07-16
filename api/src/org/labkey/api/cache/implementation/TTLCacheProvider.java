package org.labkey.api.cache.implementation;

import org.labkey.api.cache.BasicCache;
import org.labkey.api.cache.CacheProvider;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:35:38 AM
 */
public class TTLCacheProvider implements CacheProvider
{
    private static final CacheProvider INSTANCE = new TTLCacheProvider();

    public static CacheProvider getInstance()
    {
        return INSTANCE;
    }

    private TTLCacheProvider()
    {
    }

    @Override
    public <K, V> BasicCache<K, V> getBasicCache(String debugName, int limit, long defaultTimeToLive)
    {
        return new CacheImpl<K, V>(limit, defaultTimeToLive);
    }
}
