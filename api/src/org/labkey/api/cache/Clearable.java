package org.labkey.api.cache;

/**
 * User: adam
 * Date: Jul 6, 2010
 * Time: 5:55:58 PM
 */

@Deprecated
// Temporary: needed (so memtracker can clear all caches) only until we eliminate all direct uses of TTLCacheMap, CacheMap, and LimitedCacheMap
public interface Clearable
{
    void clear();
}
