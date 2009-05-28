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

import java.util.Map;
import java.util.List;
import java.util.Arrays;

/**
 * User: adam
 * Date: Apr 30, 2009
 * Time: 11:30:38 PM
 */
public class RowMapFactory<V>
{
    private final Map<String, Integer> _findMap;

    public RowMapFactory()
    {
        _findMap = new CaseInsensitiveHashMap<Integer>();
    }

    public RowMapFactory(int columns)
    {
        _findMap = new CaseInsensitiveHashMap<Integer>(2 * columns);
    }

    public RowMapFactory(Map<String, Integer> findMap)
    {
        _findMap = findMap;
    }

    public RowMapFactory(String... keys)
    {
        this(keys.length);

        for (int i = 0; i < keys.length; i++)
            _findMap.put(keys[i], i);
    }

    protected RowMap<V> getRowMap()
    {
        return new RowMap<V>(_findMap);
    }

    public RowMap<V> getRowMap(List<V> row)
    {
        return new RowMap<V>(_findMap, row);
    }

    public RowMap<V> getRowMap(V[] row)
    {
        return new RowMap<V>(_findMap, Arrays.asList(row));      // TODO: Pass through actual array?  Different class?  Static factory?
    }

    protected Map<String, Integer> getFindMap()
    {
        return _findMap;
    }
}
