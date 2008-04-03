package org.labkey.api.data.collections;

import java.util.Map;
import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Mar 20, 2006
 * Time: 11:26:38 AM
 */
public class CompareMap implements Comparator<Map>
{
    String _key;

    CompareMap(String key)
    {
        _key = key;
    }

    public int compare(Map map1, Map map2)
    {
        Comparable<Object> o1 = (Comparable<Object>) map1.get(map1.get(_key));
        Comparable<Object> o2 = (Comparable<Object>) map2.get(map2.get(_key));
        if (o1 == o2)
            return 0;
        if (null == o1)
            return -1;
        if (null == o2)
            return 1;
        return o1.compareTo(o2);
    }
}
