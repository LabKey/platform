/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

// Not currently used.  Could be if we ever need to construct a ResultSet from scratch... as opposed to stealing
// the meta data from a real ResultSet.
public class ResultSetMetaDataImpl implements ResultSetMetaData
{
    public class ColumnMetaData
    {
        public boolean isAutoIncrement = false;
        public boolean isCaseSensitive = false;
        public boolean isSearchable = true;
        public boolean isCurrency = false;
        public int isNullable = columnNullable;
        public boolean isSigned = true;
        public int columnDisplaySize = 10;
        public String columnLabel = null;
        public String columnName = null;
        public String schemaName = null;
        public int precision = 0;
        public int scale = 0;
        public String tableName = null;
        public String catalogName = null;
        public int columnType=0;
        public String columnTypeName="";
        public boolean isReadOnly = false;
        public boolean isWritable = true;
        public boolean isDefinitelyWritable = true;
        public String columnClassName = "";
    }

    ArrayList<ColumnMetaData> list;

    protected ResultSetMetaDataImpl()
    {
        list = new ArrayList<ColumnMetaData>(10);
        list.add(null);
    }

    protected void addColumn(ColumnMetaData col)
    {
        list.add(col);
    }

    public int getColumnCount() throws SQLException
    {
        return list.size()-1;
    }

    public boolean isAutoIncrement(int column) throws SQLException
    {
        return list.get(column).isAutoIncrement;
    }

    public boolean isCaseSensitive(int column) throws SQLException
    {
        return list.get(column).isCaseSensitive;
    }

    public boolean isSearchable(int column) throws SQLException
    {
        return list.get(column).isSearchable;
    }

    public boolean isCurrency(int column) throws SQLException
    {
        return list.get(column).isCurrency;
    }

    public int isNullable(int column) throws SQLException
    {
        return list.get(column).isNullable;
    }

    public boolean isSigned(int column) throws SQLException
    {
        return list.get(column).isSigned;
    }

    public int getColumnDisplaySize(int column) throws SQLException
    {
        return list.get(column).columnDisplaySize;
    }

    public String getColumnLabel(int column) throws SQLException
    {
        return list.get(column).columnLabel;
    }

    public String getColumnName(int column) throws SQLException
    {
        return list.get(column).columnName;
    }

    public String getSchemaName(int column) throws SQLException
    {
        return list.get(column).schemaName;
    }

    public int getPrecision(int column) throws SQLException
    {
        return list.get(column).precision;
    }

    public int getScale(int column) throws SQLException
    {
        return list.get(column).scale;
    }

    public String getTableName(int column) throws SQLException
    {
        return list.get(column).tableName;
    }

    public String getCatalogName(int column) throws SQLException
    {
        return list.get(column).catalogName;
    }

    public int getColumnType(int column) throws SQLException
    {
        return list.get(column).columnType;
    }

    public String getColumnTypeName(int column) throws SQLException
    {
//        if (null == list.get(column).columnTypeName)
//            list.get(column).columnTypeName = ColumnInfo.sqlTypeNameFromSqlType(list.get(column).columnType, SqlDialectMicrosoftSQLServer.getInstance());
        return list.get(column).columnTypeName;
    }

    public boolean isReadOnly(int column) throws SQLException
    {
        return list.get(column).isReadOnly;
    }

    public boolean isWritable(int column) throws SQLException
    {
        return list.get(column).isWritable;
    }

    public boolean isDefinitelyWritable(int column) throws SQLException
    {
        return list.get(column).isDefinitelyWritable;
    }

    public String getColumnClassName(int column) throws SQLException
    {
        return list.get(column).columnClassName;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new SQLException("Not a wrapper for " + iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return false;
    }
}
