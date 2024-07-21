/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: matthewb
 * Date: May 23, 2011
 * Time: 5:01:51 PM
 */
public abstract class AbstractMapDataIterator extends AbstractDataIterator implements ScrollableDataIterator, MapDataIterator, DataIteratorBuilder
{
    List<ColumnInfo> _cols = new ArrayList<>();
    Map<String, Object> _currentRowMap = null;
    int _currentRow = -1;

    protected AbstractMapDataIterator(DataIteratorContext context, Set<String> colNames)
    {
        super(context);
        _cols.add(new BaseColumnInfo(ROWNUMBER_COLUMNNAME, JdbcType.INTEGER));
        for (String name : colNames)
            _cols.add(new BaseColumnInfo(name, JdbcType.OTHER));
    }

    protected AbstractMapDataIterator(DataIteratorContext context, List<ColumnInfo> columns)
    {
        super(context);
        _cols.add(new BaseColumnInfo(ROWNUMBER_COLUMNNAME, JdbcType.INTEGER));
        _cols.addAll(columns);
    }

    @Override
    public int getColumnCount()
    {
        return _cols.size() - 1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _cols.get(i);
    }

    @Override
    public boolean supportsGetMap()
    {
        return true;
    }

    @Override
    public Map<String, Object> getMap()
    {
        assert _currentRowMap != null;
        return Collections.unmodifiableMap(_currentRowMap);
    }

    @Override
    public Object get(int i)
    {
        if (i == 0)
            return _currentRow + 1;
        return _currentRowMap.get(_cols.get(i).getName());
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        if (i == 0)
            return () -> _currentRow + 1;
        final String name = _cols.get(i).getName();
        return () -> _currentRowMap.get(name);
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        super.debugLogInfo(sb);

        _cols.forEach(p -> sb.append("    ").append(p.getName()).append("\n"));
    }


    /** For maximum efficiency, supply a List<ArrayListMap> */
    public static class ListOfMapsDataIterator extends AbstractMapDataIterator
    {
        protected List<Map<String, Object>> _rows;

        protected ListOfMapsDataIterator(DataIteratorContext context, Set<String> colsNames)
        {
            super(context, colsNames);
        }

        protected ListOfMapsDataIterator(DataIteratorContext context, List<ColumnInfo> cols)
        {
            super(context, cols);
        }

        public ListOfMapsDataIterator(DataIteratorContext context, Set<String> colNames, List<Map<String, Object>> rows)
        {
            super(context, Set.of());
            if (null == colNames || colNames.isEmpty())
            {
                var set = Sets.newCaseInsensitiveHashSet();
                for (var row : rows)
                    set.addAll(row.keySet());
                colNames = set;
            }
            for (String name : colNames)
                _cols.add(new BaseColumnInfo(name, JdbcType.OTHER));
            _rows = initRows(rows);
        }

        protected List<Map<String, Object>> initRows(List<Map<String, Object>> rows)
        {
            for (Map<String, Object> row : rows)
            {
                // assumes all ArrayListMaps are case insensitive
                if (!(row instanceof CaseInsensitiveMapWrapper || row instanceof ArrayListMap))
                {
                    return copyRows(rows);
                }
            }
            return Collections.unmodifiableList(rows);
        }

        private List<Map<String, Object>> copyRows(List<Map<String, Object>> rows)
        {
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            CaseInsensitiveMapWrapper<Object> firstWrappedRow = null;
            for (Map<String, Object> row : rows)
            {
                if (row instanceof ArrayListMap)
                {
                    result.add(row);
                }
                else
                {
                    CaseInsensitiveMapWrapper<Object> wrappedRow = row instanceof CaseInsensitiveMapWrapper<Object> wrapped ?
                            wrapped :
                            new CaseInsensitiveMapWrapper<>(row, firstWrappedRow);
                    result.add(wrappedRow);
                    if (firstWrappedRow == null)
                    {
                        firstWrappedRow = wrappedRow;
                    }
                }
            }
            return result;
        }

        @Override
        public boolean next()
        {
            ++_currentRow;
            _currentRowMap = _currentRow < _rows.size() ? _rows.get(_currentRow) : null;
            return null != _currentRowMap;
        }

        @Override
        public boolean isScrollable()
        {
            return true;
        }

        @Override
        public void beforeFirst()
        {
            _currentRow = -1;
            _currentRowMap = null;
        }

        @Override
        public void close()
        {
        }
    }

    public static DataIteratorBuilder builderOf(List<Map<String, Object>> maps)
    {
        return context -> new ListOfMapsDataIterator(context, maps.isEmpty() ? Collections.emptySet() : maps.get(0).keySet(), maps);
    }

    public static DataIterator of(List<Map<String, Object>> maps, DataIteratorContext context)
    {
        return new ListOfMapsDataIterator(context, maps.isEmpty() ? Collections.emptySet() : maps.get(0).keySet(), maps);
    }

    public static class IteratorOfMapsDataIterator extends AbstractMapDataIterator
    {
        protected Iterator<Map<String, Object>> _it;

        public IteratorOfMapsDataIterator(DataIteratorContext context, Set<String> colNames, Iterator<Map<String, Object>> it)
        {
            super(context, colNames);
            _it = it;
        }

        @Override
        public boolean next()
        {
            ++_currentRow;
            _currentRowMap = null;
            if (!_it.hasNext())
                return false;

            _currentRowMap = _it.next();
            assert _currentRowMap instanceof CaseInsensitiveHashMap<Object> || _currentRowMap instanceof ArrayListMap;
            _currentRowMap = Collections.unmodifiableMap(_currentRowMap);

            return true;
        }

        @Override
        public boolean isScrollable()
        {
            return false;
        }

        @Override
        public void beforeFirst()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws IOException
        {
            if (_it instanceof Closeable cl)
                cl.close();
        }
    }
}
