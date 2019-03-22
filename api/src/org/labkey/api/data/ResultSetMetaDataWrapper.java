/*
 * Copyright (c) 2014 LabKey Corporation
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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 9/17/2014
 * Time: 10:39 PM
 */
public class ResultSetMetaDataWrapper implements ResultSetMetaData
{
    private final ResultSetMetaData _rsmd;

    public ResultSetMetaDataWrapper(ResultSetMetaData rsmd)
    {
        _rsmd = rsmd;
    }

    @Override
    public int getColumnCount() throws SQLException
    {
        return _rsmd.getColumnCount();
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException
    {
        return _rsmd.isAutoIncrement(column);
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException
    {
        return _rsmd.isCaseSensitive(column);
    }

    @Override
    public boolean isSearchable(int column) throws SQLException
    {
        return _rsmd.isSearchable(column);
    }

    @Override
    public boolean isCurrency(int column) throws SQLException
    {
        return _rsmd.isCurrency(column);
    }

    @Override
    public int isNullable(int column) throws SQLException
    {
        return _rsmd.isNullable(column);
    }

    @Override
    public boolean isSigned(int column) throws SQLException
    {
        return _rsmd.isSigned(column);
    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException
    {
        return _rsmd.getColumnDisplaySize(column);
    }

    @Override
    public String getColumnLabel(int column) throws SQLException
    {
        return _rsmd.getColumnLabel(column);
    }

    @Override
    public String getColumnName(int column) throws SQLException
    {
        return _rsmd.getColumnName(column);
    }

    @Override
    public String getSchemaName(int column) throws SQLException
    {
        return _rsmd.getSchemaName(column);
    }

    @Override
    public int getPrecision(int column) throws SQLException
    {
        return _rsmd.getPrecision(column);
    }

    @Override
    public int getScale(int column) throws SQLException
    {
        return _rsmd.getScale(column);
    }

    @Override
    public String getTableName(int column) throws SQLException
    {
        return _rsmd.getTableName(column);
    }

    @Override
    public String getCatalogName(int column) throws SQLException
    {
        return _rsmd.getCatalogName(column);
    }

    @Override
    public int getColumnType(int column) throws SQLException
    {
        return _rsmd.getColumnType(column);
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException
    {
        return _rsmd.getColumnTypeName(column);
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException
    {
        return _rsmd.isReadOnly(column);
    }

    @Override
    public boolean isWritable(int column) throws SQLException
    {
        return _rsmd.isWritable(column);
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException
    {
        return _rsmd.isDefinitelyWritable(column);
    }

    @Override
    public String getColumnClassName(int column) throws SQLException
    {
        return _rsmd.getColumnClassName(column);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        return _rsmd.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return _rsmd.isWrapperFor(iface);
    }
}
