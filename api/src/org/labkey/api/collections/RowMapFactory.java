/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 30, 2009
 * Time: 11:30:38 PM
 */
public class RowMapFactory<V>
{
    private final ArrayListMap.FindMap<String> _findMap;

    public RowMapFactory()
    {
        _findMap = new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>());
    }

    public RowMapFactory(int columns)
    {
        _findMap = new ArrayListMap.FindMap<>(new CaseInsensitiveHashMap<>(2 * columns));
    }

    public RowMapFactory(ArrayListMap.FindMap<String> findMap)
    {
        _findMap = findMap;
    }

    public RowMapFactory(Collection<String> keys)
    {
        this(keys.size());

        int i = 0;
        for (String key : keys)
        {
            _findMap.put(key, i);
            i++;
        }
    }

    public RowMap<V> getRowMap()
    {
        return new RowMap<>(_findMap);
    }

    public RowMap<V> getRowMap(List<V> row)
    {
        return new RowMap<>(_findMap, row);
    }

    public RowMap<V> getRowMap(V... row)
    {
        return new RowMap<>(_findMap, Arrays.asList(row));      // TODO: Pass through actual array?  Different class?  Static factory?
    }

    /**
     * Will ignore values in source map that do not have values in the findmap.
     * use getRowMap().putAll() to add all values
     */
    public RowMap<V> getRowMap(Map<String, V> source)
    {
        RowMap<V> to = new RowMap<>(_findMap);
        for (String key : _findMap.keySet())
        {
            to.put(key, source.get(key));
        }
        return to;
    }    

    protected Map<String, Integer> getFindMap()
    {
        return _findMap;
    }
}
