/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import java.sql.Types;
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

    @Override
    public int getColumnCount()
    {
        return _list.size() - 1;
    }

    @Override
    public boolean isAutoIncrement(int column)
    {
        return _list.get(column).isAutoIncrement;
    }

    @Override
    public boolean isCaseSensitive(int column)
    {
        return _list.get(column).isCaseSensitive;
    }

    @Override
    public boolean isSearchable(int column)
    {
        return _list.get(column).isSearchable;
    }

    @Override
    public boolean isCurrency(int column)
    {
        return _list.get(column).isCurrency;
    }

    @Override
    public int isNullable(int column)
    {
        return _list.get(column).isNullable;
    }

    @Override
    public boolean isSigned(int column)
    {
        return _list.get(column).isSigned;
    }

    @Override
    public int getColumnDisplaySize(int column)
    {
        return _list.get(column).columnDisplaySize;
    }

    @Override
    public String getColumnLabel(int column)
    {
        return _list.get(column).columnLabel;
    }

    @Override
    public String getColumnName(int column)
    {
        return _list.get(column).columnName;
    }

    @Override
    public String getSchemaName(int column)
    {
        return _list.get(column).schemaName;
    }

    @Override
    public int getPrecision(int column)
    {
        return _list.get(column).precision;
    }

    @Override
    public int getScale(int column)
    {
        return _list.get(column).scale;
    }

    @Override
    public String getTableName(int column)
    {
        return _list.get(column).tableName;
    }

    @Override
    public String getCatalogName(int column)
    {
        return _list.get(column).catalogName;
    }

    @Override
    public int getColumnType(int column)
    {
        return _list.get(column).columnType;
    }

    @Override
    public String getColumnTypeName(int column)
    {
//        if (null == list.get(column).columnTypeName)
//            list.get(column).columnTypeName = ColumnInfo.sqlTypeNameFromSqlType(list.get(column).columnType, SqlDialectMicrosoftSQLServer.getInstance());
        return _list.get(column).columnTypeName;
    }

    @Override
    public boolean isReadOnly(int column)
    {
        return _list.get(column).isReadOnly;
    }

    @Override
    public boolean isWritable(int column)
    {
        return _list.get(column).isWritable;
    }

    @Override
    public boolean isDefinitelyWritable(int column)
    {
        return _list.get(column).isDefinitelyWritable;
    }

    @Override
    public String getColumnClassName(int column)
    {
        return _list.get(column).columnClassName;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new SQLException("Not a wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface)
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
            columnType = Types.OTHER;
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
