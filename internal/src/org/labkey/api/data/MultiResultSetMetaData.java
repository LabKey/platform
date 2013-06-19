/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.labkey.api.util.Pair;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 7, 2006
 * Time: 5:23:27 PM
 */

// Treats two or more ResultSetMetaData objects as a single "wide" ResultSetMetaData
public class MultiResultSetMetaData
{
    private Map<Integer, Pair<ResultSetMetaData, Integer>> _map;
    private int _columnCount;

    MultiResultSetMetaData(List<ResultSetMetaData> list) throws SQLException
    {
        _map = new HashMap<>(20);
        int index = 1;

        for (ResultSetMetaData rsmd : list)
        {
            int count = rsmd.getColumnCount();
            _columnCount += count;

            for (int i = 0; i < count; i++)
            {
                Pair<ResultSetMetaData, Integer> pair = new Pair<>(rsmd, count);
                _map.put(index++, pair);
            }
        }

    }

    public String getCatalogName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getCatalogName(pair.second);
    }

    public String getColumnClassName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnClassName(pair.second);
    }

    public int getColumnCount() throws SQLException
    {
        return _columnCount;
    }

    public int getColumnDisplaySize(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnDisplaySize(pair.second);
    }

    public String getColumnLabel(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnLabel(pair.second);
    }

    public String getColumnName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnName(pair.second);
    }

    public int getColumnType(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnType(pair.second);
    }

    public String getColumnTypeName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getColumnTypeName(pair.second);
    }

    public int getPrecision(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getPrecision(pair.second);
    }

    public int getScale(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getScale(pair.second);
    }

    public String getSchemaName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getSchemaName(pair.second);
    }

    public String getTableName(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.getTableName(pair.second);
    }

    public boolean isAutoIncrement(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isAutoIncrement(pair.second);
    }

    public boolean isCaseSensitive(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isCaseSensitive(pair.second);
    }

    public boolean isCurrency(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isCurrency(pair.second);
    }

    public boolean isDefinitelyWritable(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isDefinitelyWritable(pair.second);
    }

    public int isNullable(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isNullable(pair.second);
    }

    public boolean isReadOnly(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isReadOnly(pair.second);
    }

    public boolean isSearchable(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isSearchable(pair.second);
    }

    public boolean isSigned(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isSigned(pair.second);
    }

    public boolean isWritable(int column) throws SQLException
    {
        Pair<ResultSetMetaData, Integer> pair = _map.get(column);
        return pair.first.isWritable(pair.second);
    }
}
