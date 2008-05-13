/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
