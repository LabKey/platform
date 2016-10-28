/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: May 23, 2011
 * Time: 5:01:51 PM
 */
public class ListofMapsDataIterator extends AbstractDataIterator implements ScrollableDataIterator, MapDataIterator, DataIteratorBuilder
{
    List<ColumnInfo> _cols = new ArrayList<>();
    protected List<Map<String,Object>> _rows;
    int _currentRow = -1;
    

    protected ListofMapsDataIterator(List<ColumnInfo> cols)
    {
        super(null);
        _cols.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));
        _cols.addAll(cols);
    }
    

    public ListofMapsDataIterator(Set<String> colNames, List<Map<String, Object>> rows)
    {
        super(null);
        _cols.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));
        for (String name : colNames)
            _cols.add(new ColumnInfo(name));
        _rows = initRows(rows);
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        return this;
    }

    protected List<Map<String,Object>> initRows(List<Map<String,Object>> rows)
    {
        boolean debug = false;
        assert true == (debug = true);

        if (debug)
        {
            ArrayList<Map<String,Object>> copy = new ArrayList<>(rows.size());
            for (Map<String,Object> row : rows)
            {
                // assumes all ArrayListMaps are case insensitive
                assert row instanceof CaseInsensitiveMapWrapper || row instanceof ArrayListMap : "all rows must be either CaseInsensitiveMapWrapper or ArrayListMap";
                if (row instanceof ArrayListMap)
                    ((ArrayListMap)row).setReadOnly(true);
                else
                    row = Collections.unmodifiableMap(row);
                copy.add(row);
            }
            return copy;
        }
        else
        {
            return rows;
        }
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
    public boolean next() throws BatchValidationException
    {
        return ++_currentRow < _rows.size();
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
    }

    @Override
    public boolean supportsGetMap()
    {
        return true;
    }

    @Override
    public Map<String, Object> getMap()
    {
        boolean debug = false;
        assert debug = true;
        return debug ? Collections.unmodifiableMap(_rows.get(_currentRow)) : _rows.get(_currentRow);
    }

    @Override
    public Object get(int i)
    {
        if (i == 0)
            return _currentRow+1;
        return _rows.get(_currentRow).get(_cols.get(i).getName());
    }

    @Override
    public void close() throws IOException
    {
    }


    public static class Builder extends DataIteratorBuilder.Wrapper
    {
        public Builder(Set<String> colNames, List<Map<String,Object>> rows)
        {
            super(new ListofMapsDataIterator(colNames, rows));
        }
    }
}
