package org.labkey.api.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:50:02 AM
 */

// Wraps a generic BasicCache to provide a StringKeyCache.  Adds statistics gathering, removeUsingPrefix(), and debug name handling
public class StringKeyCacheWrapper<V> extends CacheWrapper<String, V> implements StringKeyCache<V>
{
    public StringKeyCacheWrapper(@NotNull BasicCache<String, V> basicCache, @NotNull String debugName, @Nullable Stats stats)
    {
        super(basicCache, debugName, stats);
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
