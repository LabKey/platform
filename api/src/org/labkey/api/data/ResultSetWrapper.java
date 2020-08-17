/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.MemTracker;

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
import java.util.Map;


public class ResultSetWrapper implements ResultSet
{
    protected ResultSet resultset;


    public ResultSetWrapper(ResultSet rs)
    {
        resultset = rs;
        MemTracker.getInstance().put(this);
    }


    @Override
    public boolean next() throws SQLException
    {
        return resultset.next();
    }


    @Override
    public void close() throws SQLException
    {
        resultset.close();
    }


    @Override
    public boolean wasNull() throws SQLException
    {
        return resultset.wasNull();
    }


    @Override
    public String getString(int i) throws SQLException
    {
        return resultset.getString(i);
    }


    @Override
    public boolean getBoolean(int i) throws SQLException
    {
        return resultset.getBoolean(i);
    }


    @Override
    public byte getByte(int i) throws SQLException
    {
        return resultset.getByte(i);
    }


    @Override
    public short getShort(int i) throws SQLException
    {
        return resultset.getShort(i);
    }


    @Override
    public int getInt(int i) throws SQLException
    {
        return resultset.getInt(i);
    }


    @Override
    public long getLong(int i) throws SQLException
    {
        return resultset.getLong(i);
    }


    @Override
    public float getFloat(int i) throws SQLException
    {
        return resultset.getFloat(i);
    }


    @Override
    public double getDouble(int i) throws SQLException
    {
        return resultset.getDouble(i);
    }


    @Override
    public BigDecimal getBigDecimal(int i, int i1) throws SQLException
    {
        return resultset.getBigDecimal(i, i1);
    }


    @Override
    public byte[] getBytes(int i) throws SQLException
    {
        return resultset.getBytes(i);
    }


    @Override
    public Date getDate(int i) throws SQLException
    {
        return resultset.getDate(i);
    }


    @Override
    public Time getTime(int i) throws SQLException
    {
        return resultset.getTime(i);
    }


    @Override
    public Timestamp getTimestamp(int i) throws SQLException
    {
        return resultset.getTimestamp(i);
    }


    @Override
    public InputStream getAsciiStream(int i) throws SQLException
    {
        return resultset.getAsciiStream(i);
    }


    @Override
    public InputStream getUnicodeStream(int i) throws SQLException
    {
        return resultset.getUnicodeStream(i);
    }


    @Override
    public InputStream getBinaryStream(int i) throws SQLException
    {
        return resultset.getBinaryStream(i);
    }


    @Override
    public String getString(String s) throws SQLException
    {
        return resultset.getString(s);
    }


    @Override
    public boolean getBoolean(String s) throws SQLException
    {
        return resultset.getBoolean(s);
    }


    @Override
    public byte getByte(String s) throws SQLException
    {
        return resultset.getByte(s);
    }


    @Override
    public short getShort(String s) throws SQLException
    {
        return resultset.getShort(s);
    }


    @Override
    public int getInt(String s) throws SQLException
    {
        return resultset.getInt(s);
    }


    @Override
    public long getLong(String s) throws SQLException
    {
        return resultset.getLong(s);
    }


    @Override
    public float getFloat(String s) throws SQLException
    {
        return resultset.getFloat(s);
    }


    @Override
    public double getDouble(String s) throws SQLException
    {
        return resultset.getDouble(s);
    }


    @Override
    public BigDecimal getBigDecimal(String s, int i) throws SQLException
    {
        return resultset.getBigDecimal(s, i);
    }


    @Override
    public byte[] getBytes(String s) throws SQLException
    {
        return resultset.getBytes(s);
    }


    @Override
    public Date getDate(String s) throws SQLException
    {
        return resultset.getDate(s);
    }


    @Override
    public Time getTime(String s) throws SQLException
    {
        return resultset.getTime(s);
    }


    @Override
    public Timestamp getTimestamp(String s) throws SQLException
    {
        return resultset.getTimestamp(s);
    }


    @Override
    public InputStream getAsciiStream(String s) throws SQLException
    {
        return resultset.getAsciiStream(s);
    }


    @Override
    public InputStream getUnicodeStream(String s) throws SQLException
    {
        return resultset.getUnicodeStream(s);
    }


    @Override
    public InputStream getBinaryStream(String s) throws SQLException
    {
        return resultset.getBinaryStream(s);
    }


    @Override
    public SQLWarning getWarnings() throws SQLException
    {
        return resultset.getWarnings();
    }


    @Override
    public void clearWarnings() throws SQLException
    {
        resultset.clearWarnings();
    }


    @Override
    public String getCursorName() throws SQLException
    {
        return resultset.getCursorName();
    }


    @Override
    public ResultSetMetaData getMetaData() throws SQLException
    {
        return resultset.getMetaData();
    }


    @Override
    public Object getObject(int i) throws SQLException
    {
        return resultset.getObject(i);
    }


    @Override
    public Object getObject(String s) throws SQLException
    {
        return resultset.getObject(s);
    }


    @Override
    public int findColumn(String s) throws SQLException
    {
        return resultset.findColumn(s);
    }


    @Override
    public Reader getCharacterStream(int i) throws SQLException
    {
        return resultset.getCharacterStream(i);
    }


    @Override
    public Reader getCharacterStream(String s) throws SQLException
    {
        return resultset.getCharacterStream(s);
    }


    @Override
    public BigDecimal getBigDecimal(int i) throws SQLException
    {
        return resultset.getBigDecimal(i);
    }


    @Override
    public BigDecimal getBigDecimal(String s) throws SQLException
    {
        return resultset.getBigDecimal(s);
    }


    @Override
    public boolean isBeforeFirst() throws SQLException
    {
        return resultset.isBeforeFirst();
    }


    @Override
    public boolean isAfterLast() throws SQLException
    {
        return resultset.isAfterLast();
    }


    @Override
    public boolean isFirst() throws SQLException
    {
        return resultset.isFirst();
    }


    @Override
    public boolean isLast() throws SQLException
    {
        return resultset.isLast();
    }


    @Override
    public void beforeFirst() throws SQLException
    {
        resultset.beforeFirst();
    }


    @Override
    public void afterLast() throws SQLException
    {
        resultset.afterLast();
    }


    @Override
    public boolean first() throws SQLException
    {
        return resultset.first();
    }


    @Override
    public boolean last() throws SQLException
    {
        return resultset.last();
    }


    @Override
    public int getRow() throws SQLException
    {
        return resultset.getRow();
    }


    @Override
    public boolean absolute(int i) throws SQLException
    {
        return resultset.absolute(i);
    }


    @Override
    public boolean relative(int i) throws SQLException
    {
        return resultset.relative(i);
    }


    @Override
    public boolean previous() throws SQLException
    {
        return resultset.previous();
    }


    @Override
    public void setFetchDirection(int i) throws SQLException
    {
        resultset.setFetchDirection(i);
    }


    @Override
    public int getFetchDirection() throws SQLException
    {
        return resultset.getFetchDirection();
    }


    @Override
    public void setFetchSize(int i) throws SQLException
    {
        resultset.setFetchSize(i);
    }


    @Override
    public int getFetchSize() throws SQLException
    {
        return resultset.getFetchSize();
    }


    @Override
    public int getType() throws SQLException
    {
        return resultset.getType();
    }


    @Override
    public int getConcurrency() throws SQLException
    {
        return resultset.getConcurrency();
    }


    @Override
    public boolean rowUpdated() throws SQLException
    {
        return resultset.rowUpdated();
    }


    @Override
    public boolean rowInserted() throws SQLException
    {
        return resultset.rowInserted();
    }


    @Override
    public boolean rowDeleted() throws SQLException
    {
        return resultset.rowDeleted();
    }


    @Override
    public void updateNull(int i) throws SQLException
    {
        resultset.updateNull(i);
    }


    @Override
    public void updateBoolean(int i, boolean b) throws SQLException
    {
        resultset.updateBoolean(i, b);
    }


    @Override
    public void updateByte(int i, byte b) throws SQLException
    {
        resultset.updateByte(i, b);
    }


    @Override
    public void updateShort(int i, short i1) throws SQLException
    {
        resultset.updateShort(i, i1);
    }


    @Override
    public void updateInt(int i, int i1) throws SQLException
    {
        resultset.updateInt(i, i1);
    }


    @Override
    public void updateLong(int i, long l) throws SQLException
    {
        resultset.updateLong(i, l);
    }


    @Override
    public void updateFloat(int i, float v) throws SQLException
    {
        resultset.updateFloat(i, v);
    }


    @Override
    public void updateDouble(int i, double v) throws SQLException
    {
        resultset.updateDouble(i, v);
    }


    @Override
    public void updateBigDecimal(int i, BigDecimal bigDecimal) throws SQLException
    {
        resultset.updateBigDecimal(i, bigDecimal);
    }


    @Override
    public void updateString(int i, String s) throws SQLException
    {
        resultset.updateString(i, s);
    }


    @Override
    public void updateBytes(int i, byte[] bytes) throws SQLException
    {
        resultset.updateBytes(i, bytes);
    }


    @Override
    public void updateDate(int i, Date date) throws SQLException
    {
        resultset.updateDate(i, date);
    }


    @Override
    public void updateTime(int i, Time time) throws SQLException
    {
        resultset.updateTime(i, time);
    }


    @Override
    public void updateTimestamp(int i, Timestamp timestamp) throws SQLException
    {
        resultset.updateTimestamp(i, timestamp);
    }


    @Override
    public void updateAsciiStream(int i, InputStream inputStream, int i1) throws SQLException
    {
        resultset.updateAsciiStream(i, inputStream, i1);
    }


    @Override
    public void updateBinaryStream(int i, InputStream inputStream, int i1) throws SQLException
    {
        resultset.updateBinaryStream(i, inputStream, i1);
    }


    @Override
    public void updateCharacterStream(int i, Reader reader, int i1) throws SQLException
    {
        resultset.updateCharacterStream(i, reader, i1);
    }


    @Override
    public void updateObject(int i, Object o, int i1) throws SQLException
    {
        resultset.updateObject(i, o, i1);
    }


    @Override
    public void updateObject(int i, Object o) throws SQLException
    {
        resultset.updateObject(i, o);
    }


    @Override
    public void updateNull(String s) throws SQLException
    {
        resultset.updateNull(s);
    }


    @Override
    public void updateBoolean(String s, boolean b) throws SQLException
    {
        resultset.updateBoolean(s, b);
    }


    @Override
    public void updateByte(String s, byte b) throws SQLException
    {
        resultset.updateByte(s, b);
    }


    @Override
    public void updateShort(String s, short i) throws SQLException
    {
        resultset.updateShort(s, i);
    }


    @Override
    public void updateInt(String s, int i) throws SQLException
    {
        resultset.updateInt(s, i);
    }


    @Override
    public void updateLong(String s, long l) throws SQLException
    {
        resultset.updateLong(s, l);
    }


    @Override
    public void updateFloat(String s, float v) throws SQLException
    {
        resultset.updateFloat(s, v);
    }


    @Override
    public void updateDouble(String s, double v) throws SQLException
    {
        resultset.updateDouble(s, v);
    }


    @Override
    public void updateBigDecimal(String s, BigDecimal bigDecimal) throws SQLException
    {
        resultset.updateBigDecimal(s, bigDecimal);
    }


    @Override
    public void updateString(String s, String s1) throws SQLException
    {
        resultset.updateString(s, s1);
    }


    @Override
    public void updateBytes(String s, byte[] bytes) throws SQLException
    {
        resultset.updateBytes(s, bytes);
    }


    @Override
    public void updateDate(String s, Date date) throws SQLException
    {
        resultset.updateDate(s, date);
    }


    @Override
    public void updateTime(String s, Time time) throws SQLException
    {
        resultset.updateTime(s, time);
    }


    @Override
    public void updateTimestamp(String s, Timestamp timestamp) throws SQLException
    {
        resultset.updateTimestamp(s, timestamp);
    }


    @Override
    public void updateAsciiStream(String s, InputStream inputStream, int i) throws SQLException
    {
        resultset.updateAsciiStream(s, inputStream, i);
    }


    @Override
    public void updateBinaryStream(String s, InputStream inputStream, int i) throws SQLException
    {
        resultset.updateBinaryStream(s, inputStream, i);
    }


    @Override
    public void updateCharacterStream(String s, Reader reader, int i) throws SQLException
    {
        resultset.updateCharacterStream(s, reader, i);
    }


    @Override
    public void updateObject(String s, Object o, int i) throws SQLException
    {
        resultset.updateObject(s, o, i);
    }


    @Override
    public void updateObject(String s, Object o) throws SQLException
    {
        resultset.updateObject(s, o);
    }


    @Override
    public void insertRow() throws SQLException
    {
        resultset.insertRow();
    }


    @Override
    public void updateRow() throws SQLException
    {
        resultset.updateRow();
    }


    @Override
    public void deleteRow() throws SQLException
    {
        resultset.deleteRow();
    }


    @Override
    public void refreshRow() throws SQLException
    {
        resultset.refreshRow();
    }


    @Override
    public void cancelRowUpdates() throws SQLException
    {
        resultset.cancelRowUpdates();
    }


    @Override
    public void moveToInsertRow() throws SQLException
    {
        resultset.moveToInsertRow();
    }


    @Override
    public void moveToCurrentRow() throws SQLException
    {
        resultset.moveToCurrentRow();
    }


    @Override
    public Statement getStatement() throws SQLException
    {
        return resultset.getStatement();
    }


    // NOTE: Generifying map was added for JDBC 4.0
    @Override
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException
    {
        return resultset.getObject(i, map);
    }


    @Override
    public Ref getRef(int i) throws SQLException
    {
        return resultset.getRef(i);
    }


    @Override
    public Blob getBlob(int i) throws SQLException
    {
        return resultset.getBlob(i);
    }


    @Override
    public Clob getClob(int i) throws SQLException
    {
        return resultset.getClob(i);
    }


    @Override
    public Array getArray(int i) throws SQLException
    {
        return resultset.getArray(i);
    }


    // NOTE: Generifying map was added for JDBC 4.0
    @Override
    public Object getObject(String s, Map<String, Class<?>> map) throws SQLException
    {
        return resultset.getObject(s, map);
    }


    @Override
    public Ref getRef(String s) throws SQLException
    {
        return resultset.getRef(s);
    }


    @Override
    public Blob getBlob(String s) throws SQLException
    {
        return resultset.getBlob(s);
    }


    @Override
    public Clob getClob(String s) throws SQLException
    {
        return resultset.getClob(s);
    }


    @Override
    public Array getArray(String s) throws SQLException
    {
        return resultset.getArray(s);
    }


    @Override
    public Date getDate(int i, Calendar calendar) throws SQLException
    {
        return resultset.getDate(i, calendar);
    }


    @Override
    public Date getDate(String s, Calendar calendar) throws SQLException
    {
        return resultset.getDate(s, calendar);
    }


    @Override
    public Time getTime(int i, Calendar calendar) throws SQLException
    {
        return resultset.getTime(i, calendar);
    }


    @Override
    public Time getTime(String s, Calendar calendar) throws SQLException
    {
        return resultset.getTime(s, calendar);
    }


    @Override
    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException
    {
        return resultset.getTimestamp(i, calendar);
    }


    @Override
    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException
    {
        return resultset.getTimestamp(s, calendar);
    }


    @Override
    public URL getURL(int i) throws SQLException
    {
        return resultset.getURL(i);
    }


    @Override
    public URL getURL(String s) throws SQLException
    {
        return resultset.getURL(s);
    }


    @Override
    public void updateRef(int i, Ref ref) throws SQLException
    {
        resultset.updateRef(i, ref);
    }


    @Override
    public void updateRef(String s, Ref ref) throws SQLException
    {
        resultset.updateRef(s, ref);
    }


    @Override
    public void updateBlob(int i, Blob blob) throws SQLException
    {
        resultset.updateBlob(i, blob);
    }


    @Override
    public void updateBlob(String s, Blob blob) throws SQLException
    {
        resultset.updateBlob(s, blob);
    }


    @Override
    public void updateClob(int i, Clob clob) throws SQLException
    {
        resultset.updateClob(i, clob);
    }


    @Override
    public void updateClob(String s, Clob clob) throws SQLException
    {
        resultset.updateClob(s, clob);
    }


    @Override
    public void updateArray(int i, Array array) throws SQLException
    {
        resultset.updateArray(i, array);
    }


    @Override
    public void updateArray(String s, Array array) throws SQLException
    {
        resultset.updateArray(s, array);
    }

    // The next group of methods was introduced in JDBC 4.0 (Java 6)

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return resultset.isWrapperFor(iface);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        return resultset.unwrap(iface);
    }

    @Override
    public int getHoldability() throws SQLException
    {
        return resultset.getHoldability();
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException
    {
        return resultset.getNCharacterStream(columnIndex);
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException
    {
        return resultset.getNCharacterStream(columnLabel);
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException
    {
        return resultset.getNClob(columnIndex);
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException
    {
        return resultset.getNClob(columnLabel);
    }

    @Override
    public String getNString(int columnIndex) throws SQLException
    {
        return resultset.getNString(columnIndex);
    }

    @Override
    public String getNString(String columnLabel) throws SQLException
    {
        return resultset.getNString(columnLabel);
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException
    {
        return resultset.getRowId(columnIndex);
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException
    {
        return resultset.getRowId(columnLabel);
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException
    {
        return resultset.getSQLXML(columnIndex);
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException
    {
        return resultset.getSQLXML(columnLabel);
    }

    @Override
    public boolean isClosed() throws SQLException
    {
        return resultset.isClosed();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
    {
        resultset.updateAsciiStream(columnIndex, x);
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        resultset.updateAsciiStream(columnIndex, x, length);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
    {
        resultset.updateAsciiStream(columnLabel, x);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        resultset.updateAsciiStream(columnLabel, x, length);
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
    {
        resultset.updateBinaryStream(columnIndex, x);
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        resultset.updateBinaryStream(columnIndex, x, length);
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
    {
        resultset.updateBinaryStream(columnLabel, x);
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        resultset.updateBinaryStream(columnLabel, x, length);
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
    {
        resultset.updateBlob(columnIndex, inputStream);
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
    {
        resultset.updateBlob(columnIndex, inputStream, length);
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
    {
        resultset.updateBlob(columnLabel, inputStream);
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
    {
        resultset.updateBlob(columnLabel, inputStream, length);
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        resultset.updateCharacterStream(columnIndex, x);
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        resultset.updateCharacterStream(columnIndex, x, length);
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateCharacterStream(columnLabel, reader);
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateCharacterStream(columnLabel, reader, length);
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException
    {
        resultset.updateClob(columnIndex, reader);
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        resultset.updateClob(columnIndex, reader, length);
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateClob(columnLabel, reader);
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateClob(columnLabel, reader, length);
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        resultset.updateNCharacterStream(columnIndex, x);
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        resultset.updateNCharacterStream(columnIndex, x, length);
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateNCharacterStream(columnLabel, reader);
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateNCharacterStream(columnLabel, reader, length);
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException
    {
        resultset.updateNClob(columnIndex, nClob);
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException
    {
        resultset.updateNClob(columnIndex, reader);
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        resultset.updateNClob(columnIndex, reader, length);
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException
    {
        resultset.updateNClob(columnLabel, nClob);
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateNClob(columnLabel, reader);
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateNClob(columnLabel, reader, length);
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException
    {
        resultset.updateNString(columnIndex, nString);
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException
    {
        resultset.updateNString(columnLabel, nString);
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException
    {
        resultset.updateRowId(columnIndex, x);
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException
    {
        resultset.updateRowId(columnLabel, x);
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
    {
        resultset.updateSQLXML(columnIndex, xmlObject);
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
    {
        resultset.updateSQLXML(columnLabel, xmlObject);
    }

    // The next group of methods was introduced in JDBC 4.1 (Java 7)

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
    {
        return resultset.getObject(columnIndex, type);
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
    {
        return resultset.getObject(columnLabel, type);
    }
}
