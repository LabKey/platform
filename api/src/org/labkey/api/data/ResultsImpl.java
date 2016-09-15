/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ResultSetUtil;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResultsImpl implements Results, DataIterator
{
    private final ResultSet _rs;
    private final Map<FieldKey, ColumnInfo> _fieldMap;  // Usually a LinkedHashMap, to keep column order
    private final Map<FieldKey, Integer> _fieldIndexMap;
    private final ArrayList<ColumnInfo> _columnInfoList;
    private int rowNumber = 0;

    @Deprecated // provide a fieldmap
    public ResultsImpl(ResultSet rs)
    {
        _rs = rs;

        try
        {
            ResultSetMetaData rsmd = rs.getMetaData();
            int count = rsmd.getColumnCount();
            _fieldMap = new LinkedHashMap<>(count * 2);
            _fieldIndexMap = new HashMap<>(count * 2);
            _columnInfoList = new ArrayList<>(count+1);
            _columnInfoList.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));

            for (int i = 1; i <= count; i++)
            {
                String label = rsmd.getColumnLabel(i);
                ColumnInfo col = new ColumnInfo(rsmd, i);
                col.setAlias(label);
                _fieldMap.put(col.getFieldKey(), col);
                _fieldIndexMap.put(col.getFieldKey(), i);
                _columnInfoList.add(col);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public ResultsImpl(ResultSet rs, @NotNull Collection<ColumnInfo> cols)
    {
        _rs = rs;
        _fieldMap = new LinkedHashMap<>(cols.size() * 2);
        _fieldIndexMap = new HashMap<>(cols.size() * 2);
        _columnInfoList = new ArrayList<>(cols.size()+1);
        _columnInfoList.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));

        for (ColumnInfo col : cols)
        {
            try
            {
                _fieldMap.put(col.getFieldKey(), col);
                int find = rs.findColumn(col.getAlias());
                _fieldIndexMap.put(col.getFieldKey(), find);
                while (_columnInfoList.size() <= find)
                    _columnInfoList.add(null);
                _columnInfoList.set(find, col);
            }
            catch (SQLException x)
            {
                TableInfo parentTable = col.getParentTable();
                String parentTableName = parentTable != null ? parentTable.getName() : "[null parent table]";
                throw new IllegalArgumentException("Column not found in resultset: [" + parentTableName + "." + col.getName() + ", " + col.getAlias() + ", " + col.getFieldKey() + "]", x);
            }
        }
    }

    public ResultsImpl(ResultSet rs, @NotNull Map<FieldKey, ColumnInfo> fieldMap)
    {
        _rs = rs;
        _fieldMap = null == fieldMap ? Collections.emptyMap() : fieldMap;
        _fieldIndexMap = new HashMap<>(_fieldMap.size() * 2);
        _columnInfoList = new ArrayList<>(fieldMap.size()+1);
        _columnInfoList.add(new ColumnInfo("_rowNumber", JdbcType.INTEGER));

        try
        {
            if (null != rs)
            {
                for (Map.Entry<FieldKey, ColumnInfo> e : _fieldMap.entrySet())
                {
                    FieldKey fk = e.getKey();
                    ColumnInfo col = e.getValue();
                    int find = rs.findColumn(col.getAlias());
                    _fieldIndexMap.put(fk, find);
                    while (_columnInfoList.size() <= find)
                        _columnInfoList.add(null);
                    _columnInfoList.set(find, col);
                }
            }
        }
        catch (SQLException x)
        {
            throw new IllegalArgumentException("Column not found in resultset");
        }
    }


    public ResultsImpl(RenderContext ctx)
    {
        this(ctx.getResults(), ctx.getFieldMap());
    }


    // TODO: Remove... not used
//    public ResultsImpl(ResultsImpl rs)
//    {
//        this._rs = rs._rs;
//        this._fieldMap = rs._fieldMap;
//        this._fieldIndexMap = rs._fieldIndexMap;
//    }
//
//
//    public ResultsImpl wrap(Results rs)
//    {
//        if (rs instanceof ResultsImpl)
//        {
//            return new ResultsImpl((ResultsImpl)rs);
//        }
//        else
//        {
//            return new ResultsImpl(rs.getResultSet(), rs.getFieldMap());
//        }
//    }
//
//


    @Override
    public ColumnInfo getColumn(int i)
    {
        return _columnInfoList.get(i);
    }


    @Override
    @NotNull
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return _fieldMap;
    }

    @Override
    @NotNull
    public Map<FieldKey, Integer> getFieldIndexMap()
    {
        return _fieldIndexMap;
    }

    // Need a FieldKey->value map in some cases (FieldKeyStringExpression, e.g.). Create a fake map that grabs values
    // directly from the ResultSet based on the fieldIndexMap. Maybe there's a better way...
    @Override
    @NotNull
    public Map<FieldKey, Object> getFieldKeyRowMap()
    {
        return new FieldKeyRowMap(this);
    }

    @Override
    @Nullable
    public ResultSet getResultSet()
    {
        return _rs;
    }


    @Override
    public int findColumn(FieldKey key) throws SQLException
    {
        Integer i = _fieldIndexMap.get(key);
        if (null == i)
            throw new SQLException(key.toString() + " not found.");
        return i;
    }

    @Override
    public boolean hasColumn(FieldKey key)
    {
        return _fieldIndexMap.containsKey(key);
    }


    @Override
    public ColumnInfo findColumnInfo(FieldKey key) throws SQLException
    {
        ColumnInfo col = _fieldMap.get(key);
        if (null == col)
            throw new SQLException(key.toString() + " not found.");
        return col;
    }


    //
    // Table.TableResultSet
    //

    // should these implement if NYI by wrapped resultset? instead of ClassCastException?

    @Override
    public boolean isComplete()
    {
        return ((TableResultSet) _rs).isComplete();
    }

    @Override
    public Map<String, Object> getRowMap() throws SQLException
    {
        return ((TableResultSet) _rs).getRowMap();
    }

    @Override
    public Iterator<Map<String, Object>> iterator()
    {
        return ((TableResultSet) _rs).iterator();
    }

    @Override
    public String getTruncationMessage(int maxRows)
    {
        return ((TableResultSet) _rs).getTruncationMessage(maxRows);
    }

    @Override
    public int getSize()
    {
        return ((TableResultSet) _rs).getSize();
    }


    //
    // FieldKey getters
    //

    @Override
    public String getString(FieldKey f)
            throws SQLException
    {
        return _rs.getString(findColumn(f));
    }

    @Override
    public boolean getBoolean(FieldKey f)
            throws SQLException
    {
        return _rs.getBoolean(findColumn(f));
    }

    @Override
    public byte getByte(FieldKey f)
            throws SQLException
    {
        return _rs.getByte(findColumn(f));
    }

    @Override
    public short getShort(FieldKey f)
            throws SQLException
    {
        return _rs.getShort(findColumn(f));
    }

    @Override
    public int getInt(FieldKey f)
            throws SQLException
    {
        return _rs.getInt(findColumn(f));
    }

    @Override
    public long getLong(FieldKey f)
            throws SQLException
    {
        return _rs.getLong(findColumn(f));
    }

    @Override
    public float getFloat(FieldKey f)
            throws SQLException
    {
        return _rs.getFloat(findColumn(f));
    }

    @Override
    public double getDouble(FieldKey f)
            throws SQLException
    {
        return _rs.getDouble(findColumn(f));
    }

    @Override
    public BigDecimal getBigDecimal(FieldKey f, int i)
            throws SQLException
    {
        return _rs.getBigDecimal(findColumn(f), i);
    }

    @Override
    public byte[] getBytes(FieldKey f)
            throws SQLException
    {
        return _rs.getBytes(findColumn(f));
    }

    @Override
    public Date getDate(FieldKey f)
            throws SQLException
    {
        return _rs.getDate(findColumn(f));
    }

    @Override
    public Time getTime(FieldKey f)
            throws SQLException
    {
        return _rs.getTime(findColumn(f));
    }

    @Override
    public Timestamp getTimestamp(FieldKey f)
            throws SQLException
    {
        return _rs.getTimestamp(findColumn(f));
    }

    @Override
    public InputStream getAsciiStream(FieldKey f)
            throws SQLException
    {
        return _rs.getAsciiStream(findColumn(f));
    }

    @Override
    public InputStream getUnicodeStream(FieldKey f)
            throws SQLException
    {
        return _rs.getUnicodeStream(findColumn(f));
    }

    @Override
    public InputStream getBinaryStream(FieldKey f)
            throws SQLException
    {
        return _rs.getBinaryStream(findColumn(f));
    }

    @Override
    public Object getObject(FieldKey f)
            throws SQLException
    {
        return _rs.getObject(findColumn(f));
    }

    @Override
    public Reader getCharacterStream(FieldKey f)
            throws SQLException
    {
        return _rs.getCharacterStream(findColumn(f));
    }

    @Override
    public BigDecimal getBigDecimal(FieldKey f)
            throws SQLException
    {
        return _rs.getBigDecimal(findColumn(f));
    }


    //
    // java.sql.Wrapper
    //

    @Override
    public <T> T unwrap(Class<T> tClass)
            throws SQLException
    {
        if (tClass.isAssignableFrom(_rs.getClass()))
            return (T) _rs;
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass)
            throws SQLException
    {
        return aClass.isAssignableFrom(_rs.getClass());
    }


    //
    // DELEGATE interface ResultSet
    //

    @Override
    public boolean next()
    {
        try
        {
            rowNumber++;
            return _rs.next();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public void close()
    {
        ResultSetUtil.close(_rs);
    }

    @Override
    public boolean wasNull()
            throws SQLException
    {
        return _rs.wasNull();
    }

    @Override
    public String getString(int i)
            throws SQLException
    {
        return _rs.getString(i);
    }

    @Override
    public boolean getBoolean(int i)
            throws SQLException
    {
        return _rs.getBoolean(i);
    }

    @Override
    public byte getByte(int i)
            throws SQLException
    {
        return _rs.getByte(i);
    }

    @Override
    public short getShort(int i)
            throws SQLException
    {
        return _rs.getShort(i);
    }

    @Override
    public int getInt(int i)
            throws SQLException
    {
        return _rs.getInt(i);
    }

    @Override
    public long getLong(int i)
            throws SQLException
    {
        return _rs.getLong(i);
    }

    @Override
    public float getFloat(int i)
            throws SQLException
    {
        return _rs.getFloat(i);
    }

    @Override
    public double getDouble(int i)
            throws SQLException
    {
        return _rs.getDouble(i);
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1)
            throws SQLException
    {
        return _rs.getBigDecimal(i, i1);
    }

    @Override
    public byte[] getBytes(int i)
            throws SQLException
    {
        return _rs.getBytes(i);
    }

    @Override
    public Date getDate(int i)
            throws SQLException
    {
        return _rs.getDate(i);
    }

    @Override
    public Time getTime(int i)
            throws SQLException
    {
        return _rs.getTime(i);
    }

    @Override
    public Timestamp getTimestamp(int i)
            throws SQLException
    {
        return _rs.getTimestamp(i);
    }

    @Override
    public InputStream getAsciiStream(int i)
            throws SQLException
    {
        return _rs.getAsciiStream(i);
    }

    @Override
    public InputStream getUnicodeStream(int i)
            throws SQLException
    {
        return _rs.getUnicodeStream(i);
    }

    @Override
    public InputStream getBinaryStream(int i)
            throws SQLException
    {
        return _rs.getBinaryStream(i);
    }

    @Override
    public String getString(String s)
            throws SQLException
    {
        return _rs.getString(s);
    }

    @Override
    public boolean getBoolean(String s)
            throws SQLException
    {
        return _rs.getBoolean(s);
    }

    @Override
    public byte getByte(String s)
            throws SQLException
    {
        return _rs.getByte(s);
    }

    @Override
    public short getShort(String s)
            throws SQLException
    {
        return _rs.getShort(s);
    }

    @Override
    public int getInt(String s)
            throws SQLException
    {
        return _rs.getInt(s);
    }

    @Override
    public long getLong(String s)
            throws SQLException
    {
        return _rs.getLong(s);
    }

    @Override
    public float getFloat(String s)
            throws SQLException
    {
        return _rs.getFloat(s);
    }

    @Override
    public double getDouble(String s)
            throws SQLException
    {
        return _rs.getDouble(s);
    }

    @Override
    public BigDecimal getBigDecimal(String s, int i)
            throws SQLException
    {
        return _rs.getBigDecimal(s, i);
    }

    @Override
    public byte[] getBytes(String s)
            throws SQLException
    {
        return _rs.getBytes(s);
    }

    @Override
    public Date getDate(String s)
            throws SQLException
    {
        return _rs.getDate(s);
    }

    @Override
    public Time getTime(String s)
            throws SQLException
    {
        return _rs.getTime(s);
    }

    @Override
    public Timestamp getTimestamp(String s)
            throws SQLException
    {
        return _rs.getTimestamp(s);
    }

    @Override
    public InputStream getAsciiStream(String s)
            throws SQLException
    {
        return _rs.getAsciiStream(s);
    }

    @Override
    public InputStream getUnicodeStream(String s)
            throws SQLException
    {
        return _rs.getUnicodeStream(s);
    }

    @Override
    public InputStream getBinaryStream(String s)
            throws SQLException
    {
        return _rs.getBinaryStream(s);
    }

    @Override
    public SQLWarning getWarnings()
            throws SQLException
    {
        return _rs.getWarnings();
    }

    @Override
    public void clearWarnings()
            throws SQLException
    {
        _rs.clearWarnings();
    }

    @Override
    public String getCursorName()
            throws SQLException
    {
        return _rs.getCursorName();
    }

    @Override
    public ResultSetMetaData getMetaData()
            throws SQLException
    {
        return _rs.getMetaData();
    }

    @Override
    public Object getObject(int i)
            throws SQLException
    {
        return _rs.getObject(i);
    }

    @Override
    public Object getObject(String s)
            throws SQLException
    {
        return _rs.getObject(s);
    }

    @Override
    public int findColumn(String s)
            throws SQLException
    {
        return _rs.findColumn(s);
    }

    @Override
    public Reader getCharacterStream(int i)
            throws SQLException
    {
        return _rs.getCharacterStream(i);
    }

    @Override
    public Reader getCharacterStream(String s)
            throws SQLException
    {
        return _rs.getCharacterStream(s);
    }

    @Override
    public BigDecimal getBigDecimal(int i)
            throws SQLException
    {
        return _rs.getBigDecimal(i);
    }

    @Override
    public BigDecimal getBigDecimal(String s)
            throws SQLException
    {
        return _rs.getBigDecimal(s);
    }

    @Override
    public boolean isBeforeFirst()
            throws SQLException
    {
        return _rs.isBeforeFirst();
    }

    @Override
    public boolean isAfterLast()
            throws SQLException
    {
        return _rs.isAfterLast();
    }

    @Override
    public boolean isFirst()
            throws SQLException
    {
        return _rs.isFirst();
    }

    @Override
    public boolean isLast()
            throws SQLException
    {
        return _rs.isLast();
    }

    @Override
    public void beforeFirst()
            throws SQLException
    {
        _rs.beforeFirst();
    }

    @Override
    public void afterLast()
            throws SQLException
    {
        _rs.afterLast();
    }

    @Override
    public boolean first()
            throws SQLException
    {
        return _rs.first();
    }

    @Override
    public boolean last()
            throws SQLException
    {
        return _rs.last();
    }

    @Override
    public int getRow()
            throws SQLException
    {
        return _rs.getRow();
    }

    @Override
    public boolean absolute(int i)
            throws SQLException
    {
        return _rs.absolute(i);
    }

    @Override
    public boolean relative(int i)
            throws SQLException
    {
        return _rs.relative(i);
    }

    @Override
    public boolean previous()
            throws SQLException
    {
        return _rs.previous();
    }

    @Override
    public void setFetchDirection(int i)
            throws SQLException
    {
        _rs.setFetchDirection(i);
    }

    @Override
    public int getFetchDirection()
            throws SQLException
    {
        return _rs.getFetchDirection();
    }

    @Override
    public void setFetchSize(int i)
            throws SQLException
    {
        _rs.setFetchSize(i);
    }

    @Override
    public int getFetchSize()
            throws SQLException
    {
        return _rs.getFetchSize();
    }

    @Override
    public int getType()
            throws SQLException
    {
        return _rs.getType();
    }

    @Override
    public int getConcurrency()
            throws SQLException
    {
        return _rs.getConcurrency();
    }

    @Override
    public boolean rowUpdated()
            throws SQLException
    {
        return _rs.rowUpdated();
    }

    @Override
    public boolean rowInserted()
            throws SQLException
    {
        return _rs.rowInserted();
    }

    @Override
    public boolean rowDeleted()
            throws SQLException
    {
        return _rs.rowDeleted();
    }

    @Override
    public void updateNull(int i)
            throws SQLException
    {
        _rs.updateNull(i);
    }

    @Override
    public void updateBoolean(int i, boolean b)
            throws SQLException
    {
        _rs.updateBoolean(i, b);
    }

    @Override
    public void updateByte(int i, byte b)
            throws SQLException
    {
        _rs.updateByte(i, b);
    }

    @Override
    public void updateShort(int i, short s)
            throws SQLException
    {
        _rs.updateShort(i, s);
    }

    @Override
    public void updateInt(int i, int i1)
            throws SQLException
    {
        _rs.updateInt(i, i1);
    }

    @Override
    public void updateLong(int i, long l)
            throws SQLException
    {
        _rs.updateLong(i, l);
    }

    @Override
    public void updateFloat(int i, float v)
            throws SQLException
    {
        _rs.updateFloat(i, v);
    }

    @Override
    public void updateDouble(int i, double v)
            throws SQLException
    {
        _rs.updateDouble(i, v);
    }

    @Override
    public void updateBigDecimal(int i, BigDecimal bigDecimal)
            throws SQLException
    {
        _rs.updateBigDecimal(i, bigDecimal);
    }

    @Override
    public void updateString(int i, String s)
            throws SQLException
    {
        _rs.updateString(i, s);
    }

    @Override
    public void updateBytes(int i, byte[] bytes)
            throws SQLException
    {
        _rs.updateBytes(i, bytes);
    }

    @Override
    public void updateDate(int i, Date date)
            throws SQLException
    {
        _rs.updateDate(i, date);
    }

    @Override
    public void updateTime(int i, Time time)
            throws SQLException
    {
        _rs.updateTime(i, time);
    }

    @Override
    public void updateTimestamp(int i, Timestamp timestamp)
            throws SQLException
    {
        _rs.updateTimestamp(i, timestamp);
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, int i1)
            throws SQLException
    {
        _rs.updateAsciiStream(i, inputStream, i1);
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, int i1)
            throws SQLException
    {
        _rs.updateBinaryStream(i, inputStream, i1);
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, int i1)
            throws SQLException
    {
        _rs.updateCharacterStream(i, reader, i1);
    }

    @Override
    public void updateObject(int i, Object o, int i1)
            throws SQLException
    {
        _rs.updateObject(i, o, i1);
    }

    @Override
    public void updateObject(int i, Object o)
            throws SQLException
    {
        _rs.updateObject(i, o);
    }

    @Override
    public void updateNull(String s)
            throws SQLException
    {
        _rs.updateNull(s);
    }

    @Override
    public void updateBoolean(String s, boolean b)
            throws SQLException
    {
        _rs.updateBoolean(s, b);
    }

    @Override
    public void updateByte(String s, byte b)
            throws SQLException
    {
        _rs.updateByte(s, b);
    }

    @Override
    public void updateShort(String s, short i)
            throws SQLException
    {
        _rs.updateShort(s, i);
    }

    @Override
    public void updateInt(String s, int i)
            throws SQLException
    {
        _rs.updateInt(s, i);
    }

    @Override
    public void updateLong(String s, long l)
            throws SQLException
    {
        _rs.updateLong(s, l);
    }

    @Override
    public void updateFloat(String s, float v)
            throws SQLException
    {
        _rs.updateFloat(s, v);
    }

    @Override
    public void updateDouble(String s, double v)
            throws SQLException
    {
        _rs.updateDouble(s, v);
    }

    @Override
    public void updateBigDecimal(String s, BigDecimal bigDecimal)
            throws SQLException
    {
        _rs.updateBigDecimal(s, bigDecimal);
    }

    @Override
    public void updateString(String s, String s1)
            throws SQLException
    {
        _rs.updateString(s, s1);
    }

    @Override
    public void updateBytes(String s, byte[] bytes)
            throws SQLException
    {
        _rs.updateBytes(s, bytes);
    }

    @Override
    public void updateDate(String s, Date date)
            throws SQLException
    {
        _rs.updateDate(s, date);
    }

    @Override
    public void updateTime(String s, Time time)
            throws SQLException
    {
        _rs.updateTime(s, time);
    }

    @Override
    public void updateTimestamp(String s, Timestamp timestamp)
            throws SQLException
    {
        _rs.updateTimestamp(s, timestamp);
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, int i)
            throws SQLException
    {
        _rs.updateAsciiStream(s, inputStream, i);
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, int i)
            throws SQLException
    {
        _rs.updateBinaryStream(s, inputStream, i);
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, int i)
            throws SQLException
    {
        _rs.updateCharacterStream(s, reader, i);
    }

    @Override
    public void updateObject(String s, Object o, int i)
            throws SQLException
    {
        _rs.updateObject(s, o, i);
    }

    @Override
    public void updateObject(String s, Object o)
            throws SQLException
    {
        _rs.updateObject(s, o);
    }

    @Override
    public void insertRow()
            throws SQLException
    {
        _rs.insertRow();
    }

    @Override
    public void updateRow()
            throws SQLException
    {
        _rs.updateRow();
    }

    @Override
    public void deleteRow()
            throws SQLException
    {
        _rs.deleteRow();
    }

    @Override
    public void refreshRow()
            throws SQLException
    {
        _rs.refreshRow();
    }

    @Override
    public void cancelRowUpdates()
            throws SQLException
    {
        _rs.cancelRowUpdates();
    }

    @Override
    public void moveToInsertRow()
            throws SQLException
    {
        _rs.moveToInsertRow();
    }

    @Override
    public void moveToCurrentRow()
            throws SQLException
    {
        _rs.moveToCurrentRow();
    }

    @Override
    public Statement getStatement()
            throws SQLException
    {
        return _rs.getStatement();
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> stringClassMap)
            throws SQLException
    {
        return _rs.getObject(i, stringClassMap);
    }

    @Override
    public Ref getRef(int i)
            throws SQLException
    {
        return _rs.getRef(i);
    }

    @Override
    public Blob getBlob(int i)
            throws SQLException
    {
        return _rs.getBlob(i);
    }

    @Override
    public Clob getClob(int i)
            throws SQLException
    {
        return _rs.getClob(i);
    }

    @Override
    public Array getArray(int i)
            throws SQLException
    {
        return _rs.getArray(i);
    }

    @Override
    public Object getObject(String s, Map<String, Class<?>> stringClassMap)
            throws SQLException
    {
        return _rs.getObject(s, stringClassMap);
    }

    @Override
    public Ref getRef(String s)
            throws SQLException
    {
        return _rs.getRef(s);
    }

    @Override
    public Blob getBlob(String s)
            throws SQLException
    {
        return _rs.getBlob(s);
    }

    @Override
    public Clob getClob(String s)
            throws SQLException
    {
        return _rs.getClob(s);
    }

    @Override
    public Array getArray(String s)
            throws SQLException
    {
        return _rs.getArray(s);
    }

    @Override
    public Date getDate(int i, Calendar calendar)
            throws SQLException
    {
        return _rs.getDate(i, calendar);
    }

    @Override
    public Date getDate(String s, Calendar calendar)
            throws SQLException
    {
        return _rs.getDate(s, calendar);
    }

    @Override
    public Time getTime(int i, Calendar calendar)
            throws SQLException
    {
        return _rs.getTime(i, calendar);
    }

    @Override
    public Time getTime(String s, Calendar calendar)
            throws SQLException
    {
        return _rs.getTime(s, calendar);
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar)
            throws SQLException
    {
        return _rs.getTimestamp(i, calendar);
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar)
            throws SQLException
    {
        return _rs.getTimestamp(s, calendar);
    }

    @Override
    public URL getURL(int i)
            throws SQLException
    {
        return _rs.getURL(i);
    }

    @Override
    public URL getURL(String s)
            throws SQLException
    {
        return _rs.getURL(s);
    }

    @Override
    public void updateRef(int i, Ref ref)
            throws SQLException
    {
        _rs.updateRef(i, ref);
    }

    @Override
    public void updateRef(String s, Ref ref)
            throws SQLException
    {
        _rs.updateRef(s, ref);
    }

    @Override
    public void updateBlob(int i, Blob blob)
            throws SQLException
    {
        _rs.updateBlob(i, blob);
    }

    @Override
    public void updateBlob(String s, Blob blob)
            throws SQLException
    {
        _rs.updateBlob(s, blob);
    }

    @Override
    public void updateClob(int i, Clob clob)
            throws SQLException
    {
        _rs.updateClob(i, clob);
    }

    @Override
    public void updateClob(String s, Clob clob)
            throws SQLException
    {
        _rs.updateClob(s, clob);
    }

    @Override
    public void updateArray(int i, Array array)
            throws SQLException
    {
        _rs.updateArray(i, array);
    }

    @Override
    public void updateArray(String s, Array array)
            throws SQLException
    {
        _rs.updateArray(s, array);
    }

    @Override
    public RowId getRowId(int i)
            throws SQLException
    {
        return _rs.getRowId(i);
    }

    @Override
    public RowId getRowId(String s)
            throws SQLException
    {
        return _rs.getRowId(s);
    }

    @Override
    public void updateRowId(int i, RowId rowId)
            throws SQLException
    {
        _rs.updateRowId(i, rowId);
    }

    @Override
    public void updateRowId(String s, RowId rowId)
            throws SQLException
    {
        _rs.updateRowId(s, rowId);
    }

    @Override
    public int getHoldability()
            throws SQLException
    {
        return _rs.getHoldability();
    }

    @Override
    public boolean isClosed()
            throws SQLException
    {
        return _rs.isClosed();
    }

    @Override
    public void updateNString(int i, String s)
            throws SQLException
    {
        _rs.updateNString(i, s);
    }

    @Override
    public void updateNString(String s, String s1)
            throws SQLException
    {
        _rs.updateNString(s, s1);
    }

    @Override
    public void updateNClob(int i, NClob nClob)
            throws SQLException
    {
        _rs.updateNClob(i, nClob);
    }

    @Override
    public void updateNClob(String s, NClob nClob)
            throws SQLException
    {
        _rs.updateNClob(s, nClob);
    }

    @Override
    public NClob getNClob(int i)
            throws SQLException
    {
        return _rs.getNClob(i);
    }

    @Override
    public NClob getNClob(String s)
            throws SQLException
    {
        return _rs.getNClob(s);
    }

    @Override
    public SQLXML getSQLXML(int i)
            throws SQLException
    {
        return _rs.getSQLXML(i);
    }

    @Override
    public SQLXML getSQLXML(String s)
            throws SQLException
    {
        return _rs.getSQLXML(s);
    }

    @Override
    public void updateSQLXML(int i, SQLXML sqlxml)
            throws SQLException
    {
        _rs.updateSQLXML(i, sqlxml);
    }

    @Override
    public void updateSQLXML(String s, SQLXML sqlxml)
            throws SQLException
    {
        _rs.updateSQLXML(s, sqlxml);
    }

    @Override
    public String getNString(int i)
            throws SQLException
    {
        return _rs.getNString(i);
    }

    @Override
    public String getNString(String s)
            throws SQLException
    {
        return _rs.getNString(s);
    }

    @Override
    public Reader getNCharacterStream(int i)
            throws SQLException
    {
        return _rs.getNCharacterStream(i);
    }

    @Override
    public Reader getNCharacterStream(String s)
            throws SQLException
    {
        return _rs.getNCharacterStream(s);
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader, long l)
            throws SQLException
    {
        _rs.updateNCharacterStream(i, reader, l);
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader, long l)
            throws SQLException
    {
        _rs.updateNCharacterStream(s, reader, l);
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateAsciiStream(i, inputStream, l);
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateBinaryStream(i, inputStream, l);
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, long l)
            throws SQLException
    {
        _rs.updateCharacterStream(i, reader, l);
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateAsciiStream(s, inputStream, l);
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateBinaryStream(s, inputStream, l);
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, long l)
            throws SQLException
    {
        _rs.updateCharacterStream(s, reader, l);
    }

    @Override
    public void updateBlob(int i, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateBlob(i, inputStream, l);
    }

    @Override
    public void updateBlob(String s, InputStream inputStream, long l)
            throws SQLException
    {
        _rs.updateBlob(s, inputStream, l);
    }

    @Override
    public void updateClob(int i, Reader reader, long l)
            throws SQLException
    {
        _rs.updateClob(i, reader, l);
    }

    @Override
    public void updateClob(String s, Reader reader, long l)
            throws SQLException
    {
        _rs.updateClob(s, reader, l);
    }

    @Override
    public void updateNClob(int i, Reader reader, long l)
            throws SQLException
    {
        _rs.updateNClob(i, reader, l);
    }

    @Override
    public void updateNClob(String s, Reader reader, long l)
            throws SQLException
    {
        _rs.updateNClob(s, reader, l);
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader)
            throws SQLException
    {
        _rs.updateNCharacterStream(i, reader);
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader)
            throws SQLException
    {
        _rs.updateNCharacterStream(s, reader);
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream)
            throws SQLException
    {
        _rs.updateAsciiStream(i, inputStream);
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream)
            throws SQLException
    {
        _rs.updateBinaryStream(i, inputStream);
    }

    @Override
    public void updateCharacterStream(int i, Reader reader)
            throws SQLException
    {
        _rs.updateCharacterStream(i, reader);
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream)
            throws SQLException
    {
        _rs.updateAsciiStream(s, inputStream);
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream)
            throws SQLException
    {
        _rs.updateBinaryStream(s, inputStream);
    }

    @Override
    public void updateCharacterStream(String s, Reader reader)
            throws SQLException
    {
        _rs.updateCharacterStream(s, reader);
    }

    @Override
    public void updateBlob(int i, InputStream inputStream)
            throws SQLException
    {
        _rs.updateBlob(i, inputStream);
    }

    @Override
    public void updateBlob(String s, InputStream inputStream)
            throws SQLException
    {
        _rs.updateBlob(s, inputStream);
    }

    @Override
    public void updateClob(int i, Reader reader)
            throws SQLException
    {
        _rs.updateClob(i, reader);
    }

    @Override
    public void updateClob(String s, Reader reader)
            throws SQLException
    {
        _rs.updateClob(s, reader);
    }

    @Override
    public void updateNClob(int i, Reader reader)
            throws SQLException
    {
        _rs.updateNClob(i, reader);
    }

    @Override
    public void updateNClob(String s, Reader reader)
            throws SQLException
    {
        _rs.updateNClob(s, reader);
    }

    // JDBC 4.1 methods below must be here so we compile on JDK 7; should implement once we require JRE 7.

    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }


    /* DataIterator */

    @Override
    public String getDebugName()
    {
        return "ResultsImpl";
    }

    @Override
    public int getColumnCount()
    {
        try
        {
            return _rs.getMetaData().getColumnCount();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _columnInfoList.get(i);
    }

    @Override
    public boolean isConstant(int i)
    {
        return false;
    }

    @Override
    public Object getConstantValue(int i)
    {
        return null;
    }

    @Override
    public Object get(int i)
    {
        try
        {
            if (i == 0)
                return rowNumber;
            else
                return _rs.getObject(i);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
