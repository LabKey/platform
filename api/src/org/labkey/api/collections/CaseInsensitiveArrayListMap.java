/*
 * Copyright (c) 2009 LabKey Corporation
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
