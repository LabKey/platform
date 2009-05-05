package org.labkey.api.collections;

import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 30, 2009
 * Time: 11:48:46 PM
 */

public class RowMap<V> extends ArrayListMap<String, V>     // TODO: Move ArrayListMap methods into here
{
    RowMap(Map<String, Integer> findMap, List<V> row)
    {
        super(findMap, row);
    }

    RowMap(Map<String, Integer> findMap)
    {
        super(findMap);
    }
}
