package org.labkey.api.data;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 8, 2007
 * Time: 1:25:29 PM
 */
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


    // The following methods are "implemented" to allow compiling and running on JDK/JRE 6.0 while still supporting
    // JDK/JRE 5.0.  If/when we require JDK/JRE 6.0, these methods should delegate to the wrapped resultset.


    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }
}
