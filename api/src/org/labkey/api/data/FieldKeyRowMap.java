/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.query.FieldKey;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldKeyRowMap implements Map<FieldKey, Object>
{
    private final Results _results;

    public FieldKeyRowMap(Results results)
    {
        _results = results;
    }

    @Override
    public int size()
    {
        return _results.getFieldIndexMap().size();
    }

    @Override
    public boolean isEmpty()
    {
        return _results.getFieldIndexMap().isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return _results.getFieldIndexMap().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key)
    {
        try
        {
            // Avoid ClassCastException if non-FieldKey is passed in. For example, StringExpressionFactory will
            // convert FieldKeys to Strings if the field doesn't exist in the map (due to PHI column or bad expression)
            return key instanceof FieldKey fk ? _results.getObject(fk) : null;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object put(FieldKey key, Object value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object remove(Object key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends FieldKey, ?> m)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<FieldKey> keySet()
    {
        return _results.getFieldIndexMap().keySet();
    }

    @Override
    public @NotNull Collection<Object> values()
    {
        List<Object> list = new LinkedList<>();

        for (FieldKey key : _results.getFieldIndexMap().keySet())
            list.add(get(key));

        return list;
    }

    @Override
    public @NotNull Set<Map.Entry<FieldKey, Object>> entrySet()
    {
        HashSet<Map.Entry<FieldKey, Object>> map = new HashSet<>(_results.getFieldIndexMap().size());

        for (FieldKey key : _results.getFieldIndexMap().keySet())
            map.add(new Entry(key));

        return map;
    }

    public static Map<String, Object> toNameMap(Map<FieldKey, Object> rowMap)
    {
        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        rowMap.forEach((key, value) -> {
            if (map.containsKey(key.getName()))
                throw new IllegalArgumentException("Duplicate key '" + key + "' found in fieldKey map.");
            map.put(key.getName(), value);
        });
        return map;
    }

    private class Entry implements Map.Entry<FieldKey, Object>
    {
        private final FieldKey _key;

        private Entry(FieldKey key)
        {
            _key = key;
        }

        @Override
        public FieldKey getKey()
        {
            return _key;
        }

        @Override
        public Object getValue()
        {
            return get(_key);
        }

        @Override
        public Object setValue(Object v)
        {
            throw new UnsupportedOperationException();
        }
    }
}
