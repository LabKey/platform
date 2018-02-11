/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * User: kevink
 * Date: 3/5/14
 *
 * Adapts a DataIterator to the Results interface.
 */
public class DataIteratorResultsImpl implements Results, TableResultSet
{
    private final DataIterator _di;
    private final Map<FieldKey, ColumnInfo> _fieldKeyMap;
    private final Map<FieldKey, Integer> _fieldKeyIndexMap;
    private final Map<String, Integer> _nameIndexMap;

    // JDBC-style 1-based row id index, 0 means before first row.
    private int _rowId = 0;

    public DataIteratorResultsImpl(DataIterator di)
    {
        _di = di;
        _fieldKeyMap = Collections.unmodifiableMap(DataIteratorUtil.createFieldKeyMap(di));
        _fieldKeyIndexMap = Collections.unmodifiableMap(DataIteratorUtil.createFieldIndexMap(di));
        _nameIndexMap = Collections.unmodifiableMap(DataIteratorUtil.createColumnAndPropertyMap(di));
    }

    @Override
    public ColumnInfo getColumn(int i)
    {
        return _di.getColumnInfo(i);
    }

    @NotNull
    @Override
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return _fieldKeyMap;
    }

    @NotNull
    @Override
    public Map<FieldKey, Integer> getFieldIndexMap()
    {
        return _fieldKeyIndexMap;
    }

    @NotNull
    @Override
    public Map<FieldKey, Object> getFieldKeyRowMap()
    {
        return new FieldKeyRowMap(this);
    }

    @Nullable
    @Override
    public ResultSet getResultSet()
    {
        return this;
    }

    @Override
    public boolean hasColumn(FieldKey key)
    {
        return _fieldKeyMap.containsKey(key);
    }

    @Override
    public int findColumn(FieldKey key) throws SQLException
    {
        Integer i = _fieldKeyIndexMap.get(key);
        if (null == i)
            throw new SQLException(key.toString() + " not found.");
        return i;
    }

    @Override
    public ColumnInfo findColumnInfo(FieldKey key) throws SQLException
    {
        ColumnInfo col = _fieldKeyMap.get(key);
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
        return true;
    }

    @Override
    public Map<String, Object> getRowMap() throws SQLException
    {
        // TODO
        return null;
    }

    @Override
    public Iterator<Map<String, Object>> iterator()
    {
        // TODO
        return null;
    }

    @Override
    public String getTruncationMessage(int maxRows)
    {
        return "Displaying only the first " + maxRows + " results.";
    }

    @Override
    public int getSize()
    {
        return -1;
    }

    //
    // FieldKey getters
    //

    @Override
    public String getString(FieldKey f)
            throws SQLException
    {
        return (String)_di.get(findColumn(f));
    }

    @Override
    public boolean getBoolean(FieldKey f)
            throws SQLException
    {
        return (boolean)_di.get(findColumn(f));
    }

    @Override
    public byte getByte(FieldKey f)
            throws SQLException
    {
        return (byte)_di.get(findColumn(f));
    }

    @Override
    public short getShort(FieldKey f)
            throws SQLException
    {
        return (short)_di.get(findColumn(f));
    }

    @Override
    public int getInt(FieldKey f)
            throws SQLException
    {
        return (int)_di.get(findColumn(f));
    }

    @Override
    public long getLong(FieldKey f)
            throws SQLException
    {
        return (long)_di.get(findColumn(f));
    }

    @Override
    public float getFloat(FieldKey f)
            throws SQLException
    {
        return (float)_di.get(findColumn(f));
    }

    @Override
    public double getDouble(FieldKey f)
            throws SQLException
    {
        return (double)_di.get(findColumn(f));
    }

    @Override
    public BigDecimal getBigDecimal(FieldKey f, int i)
            throws SQLException
    {
        return (BigDecimal)_di.get(findColumn(f));
    }

    @Override
    public byte[] getBytes(FieldKey f)
            throws SQLException
    {
        return (byte[])_di.get(findColumn(f));
    }

    @Override
    public Date getDate(FieldKey f)
            throws SQLException
    {
        return (Date)_di.get(findColumn(f));
    }

    @Override
    public Time getTime(FieldKey f)
            throws SQLException
    {
        return (Time)_di.get(findColumn(f));
    }

    @Override
    public Timestamp getTimestamp(FieldKey f)
            throws SQLException
    {
        return (Timestamp)_di.get(findColumn(f));
    }

    @Override
    public InputStream getAsciiStream(FieldKey f)
            throws SQLException
    {
        return (InputStream)_di.get(findColumn(f));
    }

    @Override
    public InputStream getUnicodeStream(FieldKey f)
            throws SQLException
    {
        return (InputStream)_di.get(findColumn(f));
    }

    @Override
    public InputStream getBinaryStream(FieldKey f)
            throws SQLException
    {
        return (InputStream)_di.get(findColumn(f));
    }

    @Override
    public Object getObject(FieldKey f)
            throws SQLException
    {
        return _di.get(findColumn(f));
    }

    @Override
    public Reader getCharacterStream(FieldKey f)
            throws SQLException
    {
        return (Reader)_di.get(findColumn(f));
    }

    @Override
    public BigDecimal getBigDecimal(FieldKey f)
            throws SQLException
    {
        return (BigDecimal)_di.get(findColumn(f));
    }

    //
    // java.sql.Wrapper
    //

    @Override
    public <T> T unwrap(Class<T> tClass)
            throws SQLException
    {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass)
            throws SQLException
    {
        return false;
    }

    //
    // DELEGATE interface ResultSet
    //

    @Override
    public boolean next()
            throws SQLException
    {
        try
        {
            boolean result = _di.next();
            if (result)
                _rowId++;
            return result;
        }
        catch (BatchValidationException e)
        {
            throw new SQLException(e);
        }
    }

    @Override
    public void close()
            throws SQLException
    {
        try
        {
            _di.close();
        }
        catch (IOException e)
        {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean wasNull()
            throws SQLException
    {
        // TODO
        return false;
    }

    @Override
    public String getString(int i)
            throws SQLException
    {
        return (String)_di.get(i);
    }

    @Override
    public boolean getBoolean(int i)
            throws SQLException
    {
        return (boolean)_di.get(i);
    }

    @Override
    public byte getByte(int i)
            throws SQLException
    {
        return (byte)_di.get(i);
    }

    @Override
    public short getShort(int i)
            throws SQLException
    {
        return (short)_di.get(i);
    }

    @Override
    public int getInt(int i)
            throws SQLException
    {
        return (int)_di.get(i);
    }

    @Override
    public long getLong(int i)
            throws SQLException
    {
        return (long)_di.get(i);
    }

    @Override
    public float getFloat(int i)
            throws SQLException
    {
        return (float)_di.get(i);
    }

    @Override
    public double getDouble(int i)
            throws SQLException
    {
        return (double)_di.get(i);
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1)
            throws SQLException
    {
        return (BigDecimal)_di.get(i);
    }

    @Override
    public byte[] getBytes(int i)
            throws SQLException
    {
        return (byte[])_di.get(i);
    }

    @Override
    public Date getDate(int i)
            throws SQLException
    {
        return (Date)_di.get(i);
    }

    @Override
    public Time getTime(int i)
            throws SQLException
    {
        return (Time)_di.get(i);
    }

    @Override
    public Timestamp getTimestamp(int i)
            throws SQLException
    {
        return (Timestamp)_di.get(i);
    }

    @Override
    public InputStream getAsciiStream(int i)
            throws SQLException
    {
        return (InputStream)_di.get(i);
    }

    @Override
    public InputStream getUnicodeStream(int i)
            throws SQLException
    {
        return (InputStream)_di.get(i);
    }

    @Override
    public InputStream getBinaryStream(int i)
            throws SQLException
    {
        return (InputStream)_di.get(i);
    }

    @Override
    public String getString(String s)
            throws SQLException
    {
        return (String)_di.get(findColumn(s));
    }

    @Override
    public boolean getBoolean(String s)
            throws SQLException
    {
        return getBoolean(findColumn(s));
    }

    @Override
    public byte getByte(String s)
            throws SQLException
    {
        return getByte(findColumn(s));
    }

    @Override
    public short getShort(String s)
            throws SQLException
    {
        return getShort(findColumn(s));
    }

    @Override
    public int getInt(String s)
            throws SQLException
    {
        return getInt(findColumn(s));
    }

    @Override
    public long getLong(String s)
            throws SQLException
    {
        return getLong(findColumn(s));
    }

    @Override
    public float getFloat(String s)
            throws SQLException
    {
        return getFloat(findColumn(s));
    }

    @Override
    public double getDouble(String s)
            throws SQLException
    {
        return getDouble(findColumn(s));
    }

    @Override
    public BigDecimal getBigDecimal(String s, int i)
            throws SQLException
    {
        return getBigDecimal(findColumn(s), i);
    }

    @Override
    public byte[] getBytes(String s)
            throws SQLException
    {
        return getBytes(findColumn(s));
    }

    @Override
    public Date getDate(String s)
            throws SQLException
    {
        return getDate(findColumn(s));
    }

    @Override
    public Time getTime(String s)
            throws SQLException
    {
        return getTime(findColumn(s));
    }

    @Override
    public Timestamp getTimestamp(String s)
            throws SQLException
    {
        return getTimestamp(findColumn(s));
    }

    @Override
    public InputStream getAsciiStream(String s)
            throws SQLException
    {
        return getAsciiStream(findColumn(s));
    }

    @Override
    public InputStream getUnicodeStream(String s)
            throws SQLException
    {
        return getUnicodeStream(findColumn(s));
    }

    @Override
    public InputStream getBinaryStream(String s)
            throws SQLException
    {
        return getBinaryStream(findColumn(s));
    }

    @Override
    public SQLWarning getWarnings()
            throws SQLException
    {
        return null;
    }

    @Override
    public void clearWarnings()
            throws SQLException
    {
    }

    @Override
    public String getCursorName()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSetMetaData getMetaData()
            throws SQLException
    {
        ResultSetMetaDataImpl md = new ResultSetMetaDataImpl();
        for (int i = 1; i <= _di.getColumnCount(); i++)
        {
            ColumnInfo col = _di.getColumnInfo(i);

            ResultSetMetaDataImpl.ColumnMetaData colMD = new ResultSetMetaDataImpl.ColumnMetaData();
            colMD.columnName = col.getName();
            colMD.columnType = col.getJdbcType().sqlType;
            colMD.columnLabel = col.getName();
            md.addColumn(colMD);
        }
        return md;
    }

    @Override
    public Object getObject(int i)
            throws SQLException
    {
        return _di.get(i);
    }

    @Override
    public Object getObject(String s)
            throws SQLException
    {
        return _di.get(findColumn(s));
    }

    @Override
    public int findColumn(String s)
            throws SQLException
    {
        Integer i = findColumn(FieldKey.fromString(s));
        if (null == i)
            i = _nameIndexMap.get(s);
        if (null == i)
            throw new SQLException(s + " not found.");
        return i;
    }

    @Override
    public Reader getCharacterStream(int i)
            throws SQLException
    {
        return (Reader)_di.get(i);
    }

    @Override
    public Reader getCharacterStream(String s)
            throws SQLException
    {
        return getCharacterStream(findColumn(s));
    }

    @Override
    public BigDecimal getBigDecimal(int i)
            throws SQLException
    {
        return (BigDecimal)_di.get(i);
    }

    @Override
    public BigDecimal getBigDecimal(String s)
            throws SQLException
    {
        return getBigDecimal(findColumn(s));
    }

    @Override
    public boolean isBeforeFirst()
            throws SQLException
    {
        return false;
    }

    @Override
    public boolean isAfterLast()
            throws SQLException
    {
        return false;
    }

    @Override
    public boolean isFirst()
            throws SQLException
    {
        return false;
    }

    @Override
    public boolean isLast()
            throws SQLException
    {
        return false;
    }

    @Override
    public void beforeFirst()
            throws SQLException
    {
    }

    @Override
    public void afterLast()
            throws SQLException
    {
    }

    @Override
    public boolean first()
            throws SQLException
    {
        return false;
    }

    @Override
    public boolean last()
            throws SQLException
    {
        return false;
    }

    @Override
    public int getRow()
            throws SQLException
    {
        return _rowId;
    }

    @Override
    public boolean absolute(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean relative(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean previous()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchDirection(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchDirection()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchSize(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchSize()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType()
            throws SQLException
    {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public int getConcurrency()
            throws SQLException
    {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public boolean rowUpdated()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rowInserted()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rowDeleted()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNull(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBoolean(int i, boolean b)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateByte(int i, byte b)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateShort(int i, short s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateInt(int i, int i1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLong(int i, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFloat(int i, float v)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDouble(int i, double v)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBigDecimal(int i, BigDecimal bigDecimal)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateString(int i, String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBytes(int i, byte[] bytes)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDate(int i, Date date)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTime(int i, Time time)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTimestamp(int i, Timestamp timestamp)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, int i1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, int i1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, int i1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(int i, Object o, int i1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(int i, Object o)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNull(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBoolean(String s, boolean b)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateByte(String s, byte b)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateShort(String s, short i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateInt(String s, int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLong(String s, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFloat(String s, float v)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDouble(String s, double v)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBigDecimal(String s, BigDecimal bigDecimal)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateString(String s, String s1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBytes(String s, byte[] bytes)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDate(String s, Date date)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTime(String s, Time time)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTimestamp(String s, Timestamp timestamp)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(String s, Object o, int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(String s, Object o)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void refreshRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelRowUpdates()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveToInsertRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveToCurrentRow()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statement getStatement()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> stringClassMap)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Ref getRef(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob getBlob(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob getClob(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array getArray(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(String s, Map<String, Class<?>> stringClassMap)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Ref getRef(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob getBlob(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob getClob(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array getArray(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(int i, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(String s, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(int i, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(String s, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getURL(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getURL(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRef(int i, Ref ref)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRef(String s, Ref ref)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int i, Blob blob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String s, Blob blob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int i, Clob clob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String s, Clob clob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArray(int i, Array array)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArray(String s, Array array)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(int i, RowId rowId)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(String s, RowId rowId)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldability()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNString(int i, String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNString(String s, String s1)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int i, NClob nClob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String s, NClob nClob)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(int i, SQLXML sqlxml)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(String s, SQLXML sqlxml)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(int i)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(String s)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int i, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String s, InputStream inputStream, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int i, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String s, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int i, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String s, Reader reader, long l)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int i, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String s, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int i, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String s, InputStream inputStream)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int i, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String s, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int i, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String s, Reader reader)
            throws SQLException
    {
        throw new UnsupportedOperationException();
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
}
