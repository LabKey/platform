/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import java.util.ArrayList;

/**
 * User: matthewb
 * Date: Jan 8, 2007
 * Time: 1:25:29 PM
 */

// Used for testing and for databases that require meta data caching.
public class ResultSetMetaDataImpl implements ResultSetMetaData
{
    private final ArrayList<ColumnMetaData> _list;

    protected ResultSetMetaDataImpl(int size)
    {
        _list = new ArrayList<>(size);
        _list.add(null);
    }

    // Default to 10 elements if not specified
    protected ResultSetMetaDataImpl()
    {
        this(10);
    }

    protected void addColumn(ColumnMetaData col)
    {
        _list.add(col);
    }

    protected void addAllColumns(ResultSetMetaData md) throws SQLException
    {
        for (int i = 1; i <= md.getColumnCount(); i++)
            addColumn(new ColumnMetaData(md, i));
    }

    public int getColumnCount() throws SQLException
    {
        return _list.size() - 1;
    }

    public boolean isAutoIncrement(int column) throws SQLException
    {
        return _list.get(column).isAutoIncrement;
    }

    public boolean isCaseSensitive(int column) throws SQLException
    {
        return _list.get(column).isCaseSensitive;
    }

    public boolean isSearchable(int column) throws SQLException
    {
        return _list.get(column).isSearchable;
    }

    public boolean isCurrency(int column) throws SQLException
    {
        return _list.get(column).isCurrency;
    }

    public int isNullable(int column) throws SQLException
    {
        return _list.get(column).isNullable;
    }

    public boolean isSigned(int column) throws SQLException
    {
        return _list.get(column).isSigned;
    }

    public int getColumnDisplaySize(int column) throws SQLException
    {
        return _list.get(column).columnDisplaySize;
    }

    public String getColumnLabel(int column) throws SQLException
    {
        return _list.get(column).columnLabel;
    }

    public String getColumnName(int column) throws SQLException
    {
        return _list.get(column).columnName;
    }

    public String getSchemaName(int column) throws SQLException
    {
        return _list.get(column).schemaName;
    }

    public int getPrecision(int column) throws SQLException
    {
        return _list.get(column).precision;
    }

    public int getScale(int column) throws SQLException
    {
        return _list.get(column).scale;
    }

    public String getTableName(int column) throws SQLException
    {
        return _list.get(column).tableName;
    }

    public String getCatalogName(int column) throws SQLException
    {
        return _list.get(column).catalogName;
    }

    public int getColumnType(int column) throws SQLException
    {
        return _list.get(column).columnType;
    }

    public String getColumnTypeName(int column) throws SQLException
    {
//        if (null == list.get(column).columnTypeName)
//            list.get(column).columnTypeName = ColumnInfo.sqlTypeNameFromSqlType(list.get(column).columnType, SqlDialectMicrosoftSQLServer.getInstance());
        return _list.get(column).columnTypeName;
    }

    public boolean isReadOnly(int column) throws SQLException
    {
        return _list.get(column).isReadOnly;
    }

    public boolean isWritable(int column) throws SQLException
    {
        return _list.get(column).isWritable;
    }

    public boolean isDefinitelyWritable(int column) throws SQLException
    {
        return _list.get(column).isDefinitelyWritable;
    }

    public String getColumnClassName(int column) throws SQLException
    {
        return _list.get(column).columnClassName;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new SQLException("Not a wrapper for " + iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return false;
    }


    public static class ColumnMetaData
    {
        public boolean isAutoIncrement;
        public boolean isCaseSensitive;
        public boolean isSearchable;
        public boolean isCurrency;
        public int isNullable;
        public boolean isSigned;
        public int columnDisplaySize;
        public String columnLabel;
        public String columnName;
        public String schemaName;
        public int precision;
        public int scale;
        public String tableName;
        public String catalogName;
        public int columnType;
        public String columnTypeName;
        public boolean isReadOnly;
        public boolean isWritable;
        public boolean isDefinitelyWritable;
        public String columnClassName;

        // Set all default values
        public ColumnMetaData()
        {
            isAutoIncrement = false;
            isCaseSensitive = false;
            isSearchable = true;
            isCurrency = false;
            isNullable = columnNullable;
            isSigned = true;
            columnDisplaySize = 10;
            columnLabel = null;
            columnName = null;
            schemaName = null;
            precision = 0;
            scale = 0;
            tableName = null;
            catalogName = null;
            columnType = 0;
            columnTypeName = "";
            isReadOnly = false;
            isWritable = true;
            isDefinitelyWritable = true;
            columnClassName = "";
        }

        // Set values based on the specified ResultSetMetaData column
        private ColumnMetaData(ResultSetMetaData md, int i) throws SQLException
        {
            catalogName = md.getCatalogName(i);
            isAutoIncrement = md.isAutoIncrement(i);
            // HACK to work around MySQL 5.7 bug. See #24800. Revisit once MySQL fixes https://bugs.mysql.com/bug.php?id=74723 and/or http://bugs.mysql.com/bug.php?id=79449
            isCaseSensitive = !"com.mysql.jdbc.ResultSetMetaData".equals(md.getClass().getName()) && md.isCaseSensitive(i);
            isSearchable = md.isSearchable(i);
            isCurrency = md.isCurrency(i);
            isNullable = md.isNullable(i);
            isSigned = md.isSigned(i);
            columnDisplaySize = md.getColumnDisplaySize(i);
            columnLabel = md.getColumnLabel(i);
            columnName = md.getColumnName(i);
            schemaName = md.getSchemaName(i);
            precision = md.getPrecision(i);
            scale = md.getScale(i);
            tableName = md.getTableName(i);
            columnType = md.getColumnType(i);
            columnTypeName = md.getColumnTypeName(i);
            isReadOnly = md.isReadOnly(i);
            isWritable = md.isWritable(i);
            isDefinitelyWritable = md.isDefinitelyWritable(i);
            columnClassName = md.getColumnClassName(i);
        }
    }
}
