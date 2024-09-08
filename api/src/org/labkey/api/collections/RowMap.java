/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

public class RowMap<V> extends ArrayListMap<String, V> implements CaseInsensitiveCollection
{
    protected RowMap()
    {
        super();
    }

    RowMap(FindMap<String> findMap, List<V> row)
    {
        super(findMap, row);
    }

    RowMap(FindMap<String> findMap)
    {
        super(findMap);
    }

    /* CONSIDER: we can optimize RowMaps to behave as if it always contains all the Keys in the find map */
}
