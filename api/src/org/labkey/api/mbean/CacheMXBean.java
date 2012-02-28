package org.labkey.api.mbean;

import org.labkey.api.cache.CacheStats;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-02-28
 * Time: 12:52 PM
 */
public interface CacheMXBean
{
    String getDebugName();
    int getLimit();
    int getSize();
    CacheStats getCacheStats();
    void clear();
}
