package org.labkey.api.collections;

import java.util.Map;
import java.util.TreeMap;

/**
* User: adam
* Date: Nov 12, 2010
* Time: 4:13:31 PM
*/
public class CaseInsensitiveTreeMap<V> extends TreeMap<String, V>
{
    public CaseInsensitiveTreeMap()
    {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    public CaseInsensitiveTreeMap(Map<String, V> map)
    {
        this();
        putAll(map);
    }
}
