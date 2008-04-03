package org.labkey.api.util;

import java.util.Map;

/**
 * User: adam
 * Date: May 19, 2006
 * Time: 6:11:59 AM
 */
public class LimitedCacheMap<K, V> extends CacheMap<K, V>
{
    private int _maxSize;

    public LimitedCacheMap(int initialSize, int maxSize)
    {
        super(initialSize);
        _maxSize = maxSize;
    }

    protected boolean removeOldestEntry(Map.Entry entry)
    {
        return (size() >= _maxSize);
    }
}

