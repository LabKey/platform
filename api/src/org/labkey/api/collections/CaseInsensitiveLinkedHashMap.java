package org.labkey.api.collections;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Map} implementation that uses case-insensitive {@link String}s for keys and an underlying LinkedHashMap,
 * so order is preserved.
 *
 * User: daveb  
 * Date: 5/17/2018
 */
public class CaseInsensitiveLinkedHashMap<V> extends CaseInsensitiveMapWrapper<V> implements Serializable
{
    public CaseInsensitiveLinkedHashMap()
    {
        super(new LinkedHashMap<>());
    }

    public CaseInsensitiveLinkedHashMap(int count)
    {
        super(new LinkedHashMap<>(count));
    }

    public CaseInsensitiveLinkedHashMap(Map<String, V> map)
    {
        this(map.size());

        for (Map.Entry<String, V> entry : map.entrySet())
            put(entry.getKey(), entry.getValue());
    }
}
