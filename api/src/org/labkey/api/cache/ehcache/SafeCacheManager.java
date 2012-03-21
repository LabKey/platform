package org.labkey.api.cache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

/**
 * User: adam
 * Date: 3/21/12
 * Time: 12:42 PM
 */

// We've run into concurrency problems during the creation/deletion of EhCache instances, see #14387.  EhCache is fixing
// this particular issue, but they recommend external synchronization, see https://jira.terracotta.org/jira/browse/EHC-931.
// This class implements that synchronization.
public class SafeCacheManager
{
    private final CacheManager _cacheManager;

    public SafeCacheManager(CacheManager cacheManager)
    {
        _cacheManager = cacheManager;
    }

    public synchronized void shutdown()
    {
        _cacheManager.shutdown();
    }

    public synchronized void addCache(Cache ehCache)
    {
        _cacheManager.addCache(ehCache);
    }

    public synchronized String[] getCacheNames()
    {
        return _cacheManager.getCacheNames();
    }

    public synchronized void removeCache(String name)
    {
        _cacheManager.removeCache(name);
    }
}
