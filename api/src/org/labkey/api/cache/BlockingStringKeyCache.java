package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 4:58 AM
 */
public class BlockingStringKeyCache<V> extends BlockingCache<String, V>
{
    public BlockingStringKeyCache(Cache<String, Object> cache)
    {
        super(cache);
    }

    public BlockingStringKeyCache(Cache<String, Object> cache, @Nullable CacheLoader<String, V> cacheLoader)
    {
        super(cache, cacheLoader);
    }

    public int removeUsingPrefix(final String prefix)
    {
        return removeUsingFilter(new Filter<String>(){
            @Override
            public boolean accept(String s)
            {
                return s.startsWith(prefix);
            }
        });
    }
}
