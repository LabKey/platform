package org.labkey.api.cache;

import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jun 20, 2010
 * Time: 3:12:42 PM
 */
public class StringKeyCacheImpl<V> extends CacheImpl<String, V> implements StringKeyCache<V>
{
    public StringKeyCacheImpl(int size, long defaultTimeToLive, String debugName)
    {
        super(size, defaultTimeToLive, debugName);
    }

    public void removeUsingPrefix(final String prefix)
    {
        removeUsingFilter(new Filter<String>(){
            @Override
            public boolean accept(String s)
            {
                return s.startsWith(prefix);
            }
        });
    }
}
