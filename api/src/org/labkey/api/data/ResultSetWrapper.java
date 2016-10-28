/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
import java.sql.*;
import java.sql.SQLXML;
import java.sql.RowId;
import java.sql.NClob;
import java.util.Calendar;
import java.util.Map;


public class ResultSetWrapper implements ResultSet
{
    protected ResultSet resultset = null;


    public ResultSetWrapper(ResultSet rs)
    {
        resultset = rs;
        MemTracker.getInstance().put(this);
    }


    public boolean next() throws SQLException
    {
        return resultset.next();
    }


    public void close() throws SQLException
    {
        resultset.close();
    }


    public boolean wasNull() throws SQLException
    {
        return resultset.wasNull();
    }


    public String getString(int i) throws SQLException
    {
        return resultset.getString(i);
    }


    public boolean getBoolean(int i) throws SQLException
    {
        return resultset.getBoolean(i);
    }


    public byte getByte(int i) throws SQLException
    {
        return resultset.getByte(i);
    }


    public short getShort(int i) throws SQLException
    {
        return resultset.getShort(i);
    }


    public int getInt(int i) throws SQLException
    {
        return resultset.getInt(i);
    }


    public long getLong(int i) throws SQLException
    {
        return resultset.getLong(i);
    }


    public float getFloat(int i) throws SQLException
    {
        return resultset.getFloat(i);
    }


    public double getDouble(int i) throws SQLException
    {
        return resultset.getDouble(i);
    }


    public BigDecimal getBigDecimal(int i, int i1) throws SQLException
    {
        return resultset.getBigDecimal(i, i1);
    }


    public byte[] getBytes(int i) throws SQLException
    {
        return resultset.getBytes(i);
    }


    public Date getDate(int i) throws SQLException
    {
        return resultset.getDate(i);
    }


    public Time getTime(int i) throws SQLException
    {
        return resultset.getTime(i);
    }


    public Timestamp getTimestamp(int i) throws SQLException
    {
        return resultset.getTimestamp(i);
    }


    public InputStream getAsciiStream(int i) throws SQLException
    {
        return resultset.getAsciiStream(i);
    }


    public InputStream getUnicodeStream(int i) throws SQLException
    {
        return resultset.getUnicodeStream(i);
    }


    public InputStream getBinaryStream(int i) throws SQLException
    {
        return resultset.getBinaryStream(i);
    }


    public String getString(String s) throws SQLException
    {
        return resultset.getString(s);
    }


    public boolean getBoolean(String s) throws SQLException
    {
        return resultset.getBoolean(s);
    }


    public byte getByte(String s) throws SQLException
    {
        return resultset.getByte(s);
    }


    public short getShort(String s) throws SQLException
    {
        return resultset.getShort(s);
    }


    public int getInt(String s) throws SQLException
    {
        return resultset.getInt(s);
    }


    public long getLong(String s) throws SQLException
    {
        return resultset.getLong(s);
    }


    public float getFloat(String s) throws SQLException
    {
        return resultset.getFloat(s);
    }


    public double getDouble(String s) throws SQLException
    {
        return resultset.getDouble(s);
    }


    public BigDecimal getBigDecimal(String s, int i) throws SQLException
    {
        return resultset.getBigDecimal(s, i);
    }


    public byte[] getBytes(String s) throws SQLException
    {
        return resultset.getBytes(s);
    }


    public Date getDate(String s) throws SQLException
    {
        return resultset.getDate(s);
    }


    public Time getTime(String s) throws SQLException
    {
        return resultset.getTime(s);
    }


    public Timestamp getTimestamp(String s) throws SQLException
    {
        return resultset.getTimestamp(s);
    }


    public InputStream getAsciiStream(String s) throws SQLException
    {
        return resultset.getAsciiStream(s);
    }


    public InputStream getUnicodeStream(String s) throws SQLException
    {
        return resultset.getUnicodeStream(s);
    }


    public InputStream getBinaryStream(String s) throws SQLException
    {
        return resultset.getBinaryStream(s);
    }


    public SQLWarning getWarnings() throws SQLException
    {
        return resultset.getWarnings();
    }


    public void clearWarnings() throws SQLException
    {
        resultset.clearWarnings();
    }


    public String getCursorName() throws SQLException
    {
        return resultset.getCursorName();
    }


    public ResultSetMetaData getMetaData() throws SQLException
    {
        return resultset.getMetaData();
    }


    public Object getObject(int i) throws SQLException
    {
        return resultset.getObject(i);
    }


    public Object getObject(String s) throws SQLException
    {
        return resultset.getObject(s);
    }


    public int findColumn(String s) throws SQLException
    {
        return resultset.findColumn(s);
    }


    public Reader getCharacterStream(int i) throws SQLException
    {
        return resultset.getCharacterStream(i);
    }


    public Reader getCharacterStream(String s) throws SQLException
    {
        return resultset.getCharacterStream(s);
    }


    public BigDecimal getBigDecimal(int i) throws SQLException
    {
        return resultset.getBigDecimal(i);
    }


    public BigDecimal getBigDecimal(String s) throws SQLException
    {
        return resultset.getBigDecimal(s);
    }


    public boolean isBeforeFirst() throws SQLException
    {
        return resultset.isBeforeFirst();
    }


    public boolean isAfterLast() throws SQLException
    {
        return resultset.isAfterLast();
    }


    public boolean isFirst() throws SQLException
    {
        return resultset.isFirst();
    }


    public boolean isLast() throws SQLException
    {
        return resultset.isLast();
    }


    public void beforeFirst() throws SQLException
    {
        resultset.beforeFirst();
    }


    public void afterLast() throws SQLException
    {
        resultset.afterLast();
    }


    public boolean first() throws SQLException
    {
        return resultset.first();
    }


    public boolean last() throws SQLException
    {
        return resultset.last();
    }


    public int getRow() throws SQLException
    {
        return resultset.getRow();
    }


    public boolean absolute(int i) throws SQLException
    {
        return resultset.absolute(i);
    }


    public boolean relative(int i) throws SQLException
    {
        return resultset.relative(i);
    }


    public boolean previous() throws SQLException
    {
        return resultset.previous();
    }


    public void setFetchDirection(int i) throws SQLException
    {
        resultset.setFetchDirection(i);
    }


    public int getFetchDirection() throws SQLException
    {
        return resultset.getFetchDirection();
    }


    public void setFetchSize(int i) throws SQLException
    {
        resultset.setFetchSize(i);
    }


    public int getFetchSize() throws SQLException
    {
        return resultset.getFetchSize();
    }


    public int getType() throws SQLException
    {
        return resultset.getType();
    }


    public int getConcurrency() throws SQLException
    {
        return resultset.getConcurrency();
    }


    public boolean rowUpdated() throws SQLException
    {
        return resultset.rowUpdated();
    }


    public boolean rowInserted() throws SQLException
    {
        return resultset.rowInserted();
    }


    public boolean rowDeleted() throws SQLException
    {
        return resultset.rowDeleted();
    }


    public void updateNull(int i) throws SQLException
    {
        resultset.updateNull(i);
    }


    public void updateBoolean(int i, boolean b) throws SQLException
    {
        resultset.updateBoolean(i, b);
    }


    public void updateByte(int i, byte b) throws SQLException
    {
        resultset.updateByte(i, b);
    }


    public void updateShort(int i, short i1) throws SQLException
    {
        resultset.updateShort(i, i1);
    }


    public void updateInt(int i, int i1) throws SQLException
    {
        resultset.updateInt(i, i1);
    }


    public void updateLong(int i, long l) throws SQLException
    {
        resultset.updateLong(i, l);
    }


    public void updateFloat(int i, float v) throws SQLException
    {
        resultset.updateFloat(i, v);
    }


    public void updateDouble(int i, double v) throws SQLException
    {
        resultset.updateDouble(i, v);
    }


    public void updateBigDecimal(int i, BigDecimal bigDecimal) throws SQLException
    {
        resultset.updateBigDecimal(i, bigDecimal);
    }


    public void updateString(int i, String s) throws SQLException
    {
        resultset.updateString(i, s);
    }


    public void updateBytes(int i, byte[] bytes) throws SQLException
    {
        resultset.updateBytes(i, bytes);
    }


    public void updateDate(int i, Date date) throws SQLException
    {
        resultset.updateDate(i, date);
    }


    public void updateTime(int i, Time time) throws SQLException
    {
        resultset.updateTime(i, time);
    }


    public void updateTimestamp(int i, Timestamp timestamp) throws SQLException
    {
        resultset.updateTimestamp(i, timestamp);
    }


    public void updateAsciiStream(int i, InputStream inputStream, int i1) throws SQLException
    {
        resultset.updateAsciiStream(i, inputStream, i1);
    }


    public void updateBinaryStream(int i, InputStream inputStream, int i1) throws SQLException
    {
        resultset.updateBinaryStream(i, inputStream, i1);
    }


    public void updateCharacterStream(int i, Reader reader, int i1) throws SQLException
    {
        resultset.updateCharacterStream(i, reader, i1);
    }


    public void updateObject(int i, Object o, int i1) throws SQLException
    {
        resultset.updateObject(i, o, i1);
    }


    public void updateObject(int i, Object o) throws SQLException
    {
        resultset.updateObject(i, o);
    }


    public void updateNull(String s) throws SQLException
    {
        resultset.updateNull(s);
    }


    public void updateBoolean(String s, boolean b) throws SQLException
    {
        resultset.updateBoolean(s, b);
    }


    public void updateByte(String s, byte b) throws SQLException
    {
        resultset.updateByte(s, b);
    }


    public void updateShort(String s, short i) throws SQLException
    {
        resultset.updateShort(s, i);
    }


    public void updateInt(String s, int i) throws SQLException
    {
        resultset.updateInt(s, i);
    }


    public void updateLong(String s, long l) throws SQLException
    {
        resultset.updateLong(s, l);
    }


    public void updateFloat(String s, float v) throws SQLException
    {
        resultset.updateFloat(s, v);
    }


    public void updateDouble(String s, double v) throws SQLException
    {
        resultset.updateDouble(s, v);
    }


    public void updateBigDecimal(String s, BigDecimal bigDecimal) throws SQLException
    {
        resultset.updateBigDecimal(s, bigDecimal);
    }


    public void updateString(String s, String s1) throws SQLException
    {
        resultset.updateString(s, s1);
    }


    public void updateBytes(String s, byte[] bytes) throws SQLException
    {
        resultset.updateBytes(s, bytes);
    }


    public void updateDate(String s, Date date) throws SQLException
    {
        resultset.updateDate(s, date);
    }


    public void updateTime(String s, Time time) throws SQLException
    {
        resultset.updateTime(s, time);
    }


    public void updateTimestamp(String s, Timestamp timestamp) throws SQLException
    {
        resultset.updateTimestamp(s, timestamp);
    }


    public void updateAsciiStream(String s, InputStream inputStream, int i) throws SQLException
    {
        resultset.updateAsciiStream(s, inputStream, i);
    }


    public void updateBinaryStream(String s, InputStream inputStream, int i) throws SQLException
    {
        resultset.updateBinaryStream(s, inputStream, i);
    }


    public void updateCharacterStream(String s, Reader reader, int i) throws SQLException
    {
        resultset.updateCharacterStream(s, reader, i);
    }


    public void updateObject(String s, Object o, int i) throws SQLException
    {
        resultset.updateObject(s, o, i);
    }


    public void updateObject(String s, Object o) throws SQLException
    {
        resultset.updateObject(s, o);
    }


    public void insertRow() throws SQLException
    {
        resultset.insertRow();
    }


    public void updateRow() throws SQLException
    {
        resultset.updateRow();
    }


    public void deleteRow() throws SQLException
    {
        resultset.deleteRow();
    }


    public void refreshRow() throws SQLException
    {
        resultset.refreshRow();
    }


    public void cancelRowUpdates() throws SQLException
    {
        resultset.cancelRowUpdates();
    }


    public void moveToInsertRow() throws SQLException
    {
        resultset.moveToInsertRow();
    }


    public void moveToCurrentRow() throws SQLException
    {
        resultset.moveToCurrentRow();
    }


    public Statement getStatement() throws SQLException
    {
        return resultset.getStatement();
    }


    // NOTE: Generifying map was added for JDBC 4.0
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException
    {
        return resultset.getObject(i, map);
    }


    public Ref getRef(int i) throws SQLException
    {
        return resultset.getRef(i);
    }


    public Blob getBlob(int i) throws SQLException
    {
        return resultset.getBlob(i);
    }


    public Clob getClob(int i) throws SQLException
    {
        return resultset.getClob(i);
    }


    public Array getArray(int i) throws SQLException
    {
        return resultset.getArray(i);
    }


    // NOTE: Generifying map was added for JDBC 4.0
    public Object getObject(String s, Map<String, Class<?>> map) throws SQLException
    {
        return resultset.getObject(s, map);
    }


    public Ref getRef(String s) throws SQLException
    {
        return resultset.getRef(s);
    }


    public Blob getBlob(String s) throws SQLException
    {
        return resultset.getBlob(s);
    }


    public Clob getClob(String s) throws SQLException
    {
        return resultset.getClob(s);
    }


    public Array getArray(String s) throws SQLException
    {
        return resultset.getArray(s);
    }


    public Date getDate(int i, Calendar calendar) throws SQLException
    {
        return resultset.getDate(i, calendar);
    }


    public Date getDate(String s, Calendar calendar) throws SQLException
    {
        return resultset.getDate(s, calendar);
    }


    public Time getTime(int i, Calendar calendar) throws SQLException
    {
        return resultset.getTime(i, calendar);
    }


    public Time getTime(String s, Calendar calendar) throws SQLException
    {
        return resultset.getTime(s, calendar);
    }


    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException
    {
        return resultset.getTimestamp(i, calendar);
    }


    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException
    {
        return resultset.getTimestamp(s, calendar);
    }


    public URL getURL(int i) throws SQLException
    {
        return resultset.getURL(i);
    }


    public URL getURL(String s) throws SQLException
    {
        return resultset.getURL(s);
    }


    public void updateRef(int i, Ref ref) throws SQLException
    {
        resultset.updateRef(i, ref);
    }


    public void updateRef(String s, Ref ref) throws SQLException
    {
        resultset.updateRef(s, ref);
    }


    public void updateBlob(int i, Blob blob) throws SQLException
    {
        resultset.updateBlob(i, blob);
    }


    public void updateBlob(String s, Blob blob) throws SQLException
    {
        resultset.updateBlob(s, blob);
    }


    public void updateClob(int i, Clob clob) throws SQLException
    {
        resultset.updateClob(i, clob);
    }


    public void updateClob(String s, Clob clob) throws SQLException
    {
        resultset.updateClob(s, clob);
    }


    public void updateArray(int i, Array array) throws SQLException
    {
        resultset.updateArray(i, array);
    }


    public void updateArray(String s, Array array) throws SQLException
    {
        resultset.updateArray(s, array);
    }

    // The next group of methods was introduced in JDBC 4.0 (Java 6)

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return resultset.isWrapperFor(iface);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        return resultset.unwrap(iface);
    }

    public int getHoldability() throws SQLException
    {
        return resultset.getHoldability();
    }

    public Reader getNCharacterStream(int columnIndex) throws SQLException
    {
        return resultset.getNCharacterStream(columnIndex);
    }

    public Reader getNCharacterStream(String columnLabel) throws SQLException
    {
        return resultset.getNCharacterStream(columnLabel);
    }

    public NClob getNClob(int columnIndex) throws SQLException
    {
        return resultset.getNClob(columnIndex);
    }

    public NClob getNClob(String columnLabel) throws SQLException
    {
        return resultset.getNClob(columnLabel);
    }

    public String getNString(int columnIndex) throws SQLException
    {
        return resultset.getNString(columnIndex);
    }

    public String getNString(String columnLabel) throws SQLException
    {
        return resultset.getNString(columnLabel);
    }

    public RowId getRowId(int columnIndex) throws SQLException
    {
        return resultset.getRowId(columnIndex);
    }

    public RowId getRowId(String columnLabel) throws SQLException
    {
        return resultset.getRowId(columnLabel);
    }

    public SQLXML getSQLXML(int columnIndex) throws SQLException
    {
        return resultset.getSQLXML(columnIndex);
    }

    public SQLXML getSQLXML(String columnLabel) throws SQLException
    {
        return resultset.getSQLXML(columnLabel);
    }

    public boolean isClosed() throws SQLException
    {
        return resultset.isClosed();
    }

    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
    {
        resultset.updateAsciiStream(columnIndex, x);
    }

    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        resultset.updateAsciiStream(columnIndex, x, length);
    }

    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
    {
        resultset.updateAsciiStream(columnLabel, x);
    }

    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        resultset.updateAsciiStream(columnLabel, x, length);
    }

    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
    {
        resultset.updateBinaryStream(columnIndex, x);
    }

    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
    {
        resultset.updateBinaryStream(columnIndex, x, length);
    }

    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
    {
        resultset.updateBinaryStream(columnLabel, x);
    }

    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
    {
        resultset.updateBinaryStream(columnLabel, x, length);
    }

    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
    {
        resultset.updateBlob(columnIndex, inputStream);
    }

    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
    {
        resultset.updateBlob(columnIndex, inputStream, length);
    }

    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
    {
        resultset.updateBlob(columnLabel, inputStream);
    }

    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
    {
        resultset.updateBlob(columnLabel, inputStream, length);
    }

    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        resultset.updateCharacterStream(columnIndex, x);
    }

    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        resultset.updateCharacterStream(columnIndex, x, length);
    }

    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateCharacterStream(columnLabel, reader);
    }

    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateCharacterStream(columnLabel, reader, length);
    }

    public void updateClob(int columnIndex, Reader reader) throws SQLException
    {
        resultset.updateClob(columnIndex, reader);
    }

    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        resultset.updateClob(columnIndex, reader, length);
    }

    public void updateClob(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateClob(columnLabel, reader);
    }

    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateClob(columnLabel, reader, length);
    }

    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
    {
        resultset.updateNCharacterStream(columnIndex, x);
    }

    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
    {
        resultset.updateNCharacterStream(columnIndex, x, length);
    }

    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateNCharacterStream(columnLabel, reader);
    }

    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateNCharacterStream(columnLabel, reader, length);
    }

    public void updateNClob(int columnIndex, NClob nClob) throws SQLException
    {
        resultset.updateNClob(columnIndex, nClob);
    }

    public void updateNClob(int columnIndex, Reader reader) throws SQLException
    {
        resultset.updateNClob(columnIndex, reader);
    }

    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
    {
        resultset.updateNClob(columnIndex, reader, length);
    }

    public void updateNClob(String columnLabel, NClob nClob) throws SQLException
    {
        resultset.updateNClob(columnLabel, nClob);
    }

    public void updateNClob(String columnLabel, Reader reader) throws SQLException
    {
        resultset.updateNClob(columnLabel, reader);
    }

    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
    {
        resultset.updateNClob(columnLabel, reader, length);
    }

    public void updateNString(int columnIndex, String nString) throws SQLException
    {
        resultset.updateNString(columnIndex, nString);
    }

    public void updateNString(String columnLabel, String nString) throws SQLException
    {
        resultset.updateNString(columnLabel, nString);
    }

    public void updateRowId(int columnIndex, RowId x) throws SQLException
    {
        resultset.updateRowId(columnIndex, x);
    }

    public void updateRowId(String columnLabel, RowId x) throws SQLException
    {
        resultset.updateRowId(columnLabel, x);
    }

    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
    {
        resultset.updateSQLXML(columnIndex, xmlObject);
    }

    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
    {
        resultset.updateSQLXML(columnLabel, xmlObject);
    }

    // The next group of methods was introduced in JDBC 4.1 (Java 7)

    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
    {
        return resultset.getObject(columnIndex, type);
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
    {
        return resultset.getObject(columnLabel, type);
    }
}
