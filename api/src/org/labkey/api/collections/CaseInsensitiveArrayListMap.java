package org.labkey.api.collections;

import java.util.List;
import java.util.Map;

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

    public CaseInsensitiveArrayListMap(int rowCount)
    {
        super(new CaseInsensitiveHashMap<Integer>(), rowCount);
    }

    public CaseInsensitiveArrayListMap(CaseInsensitiveArrayListMap<V> cialm, int rowCount)
    {
        super(cialm.getFindMap(), rowCount);
    }

    public CaseInsensitiveArrayListMap(CaseInsensitiveArrayListMap<V> cialm, List<V> row)
    {
        super(cialm.getFindMap(), row);
    }

    public CaseInsensitiveArrayListMap(Map<String, Integer> findMap, List<V> row)
    {
        super(findMap, row);
    }
}
