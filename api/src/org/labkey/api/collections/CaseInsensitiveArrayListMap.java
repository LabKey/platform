package org.labkey.api.collections;

/**
 * User: adam
 * Date: Apr 29, 2009
 * Time: 3:15:50 PM
 */
public class CaseInsensitiveArrayListMap<V> extends ArrayListMap<String, V>
{
    public CaseInsensitiveArrayListMap()
    {
        super(new CaseInsensitiveHashMap<Integer>());
    }
}
