package org.labkey.api.cache;

/**
* User: adam
* Date: 1/15/12
* Time: 10:23 PM
*/
public class Wrapper<V>
{
    @SuppressWarnings({"unchecked"})
    protected V value = (V) BlockingCache.UNINITIALIZED;

    void setValue(V v)
    {
        value = v;
    }

    public V getValue()
    {
        return value == BlockingCache.UNINITIALIZED ? null : value;
    }
}
