/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMap;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;


/**
 * In-memory representation of a ResultSet, no longer directly backed by a database connection
 * User: mbellew
 * Date: Nov 29, 2005
 */
public class CachedResultSet implements ResultSet, TableResultSet
{
    private static final Logger _log = LogManager.getLogger(CachedResultSet.class);

    // metadata
    private final ResultSetMetaData _md;
    private final HashMap<String, Integer> _columns;

    // data
    private final ArrayList<RowMap<Object>> _rowMaps;
    private final boolean _isComplete;
    @Nullable
    private final StackTraceElement[] _stackTrace;
    private final String _threadName;

    private boolean _wasClosed = false;
    private boolean _requireClose = true;
    private String _url = null;

    // state
    private int _row = -1;
    private int _direction = 1;
    private int _fetchSize = 1;
    private Object _lastObject = null;


    /*
        Constructor is not normally used... see CachedResultSets for static factory methods.

        stackTrace is used to set an alternate stack trace -- good for async queries, to indicate original creation stack trace
     */
    CachedResultSet(ResultSetMetaData md, boolean cacheMetaData, ArrayList<RowMap<Object>> maps, boolean isComplete, @Nullable StackTraceElement[] stackTrace)
    {
        _rowMaps = maps;
        _isComplete = isComplete;

        try
        {
            _md = cacheMetaData ? new CachedResultSetMetaData(md) : md;
            _columns = new HashMap<>(_md.getColumnCount() * 2);

            for (int col = _md.getColumnCount(); col >= 1; col--)
            {
                // Use getColumnLabel() (not getColumnName()) to better match JDBC 4.0 and to work on MySQL, #19869
                String colLabel = _md.getColumnLabel(col).toLowerCase();
                assert !_columns.containsKey(colLabel) : "Duplicate column label: " + colLabel;
                _columns.put(colLabel, col);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        if (MiniProfiler.isCollectTroubleshootingStackTraces())
        {
            // Stash stack trace that created this CachedRowSet
            if (null != stackTrace)
            {
                _stackTrace = stackTrace;
            }
            else
            {
                _stackTrace = MiniProfiler.getTroubleshootingStackTrace();
            }

            _threadName = Thread.currentThread().getName();

            if (HttpView.getStackSize() > 0)
            {
                try
                {
                    _url = ViewServlet.getOriginalURL();
                }
                catch (Exception x)
                {
                    // we might not be in a view thread...
                }
            }
        }
        else
        {
            _stackTrace = null;
            _threadName = null;
        }

        MemTracker.getInstance().put(this);
    }

    public boolean isRequireClose()
    {
        return _requireClose;
    }

    public CachedResultSet setRequireClose(boolean requireClose)
    {
        _requireClose = requireClose;
        return this;
    }

    @Override
    public @Nullable Connection getConnection() throws SQLException
    {
        return null;
    }

//
    // ResultSet
    //

    @Override
    public void setFetchDirection(int direction)
    {
        //UNDONE: does this affect next()/prev() or not???
        _direction = direction == FETCH_REVERSE ? -1 : 1;
    }

    @Override
    public int getFetchDirection()
    {
        return _direction == 1 ? FETCH_FORWARD : FETCH_REVERSE;
    }

    @Override
    public void setFetchSize(int rows)
    {
        _fetchSize = rows;
    }

    @Override
    public int getFetchSize()
    {
        return _fetchSize;
    }

    @Override
    public int getType()
    {
        return TYPE_SCROLL_INSENSITIVE;
    }

    @Override
    public int getConcurrency()
    {
        return CONCUR_READ_ONLY;
    }

    @Override
    public boolean next()
    {
        return relative(_direction);
    }

    @Override
    public void close()
    {
        _wasClosed = true;
    }

    @Override
    public boolean wasNull()
    {
        return _lastObject == null;
    }

    //
    //  getDATA()
    //

    @Override
    public String getString(int columnIndex) throws SQLException
    {
        return _string(getObject(columnIndex));
    }

    @Override
    public String getString(String columnName) throws SQLException
    {
        return _string(getObject(columnName));
    }

    @SuppressWarnings({"UNUSED_THROWS"})
    private String _string(Object o)
    {
        if (null == o)
            return null;
        if (o instanceof String)
            return (String) o;
        return ConvertUtils.convert(o);
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException
    {
        return _boolean(getObject(columnIndex));
    }

    @Override
    public boolean getBoolean(String columnName) throws SQLException
    {
        return _boolean(getObject(columnName));
    }

    private boolean _boolean(Object o) throws SQLException
    {
        if (null == o)
            return false;
        if (o instanceof Boolean)
            return (Boolean) o;
        if (o instanceof Number)
            return ((Number) o).intValue() != 0;
        throwConversionError("Can't convert '" + o.getClass() + "' to boolean");
        return false;
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException
    {
        return _byte(getObject(columnIndex));
    }

    @Override
    public byte getByte(String columnName) throws SQLException
    {
        return _byte(getObject(columnName));
    }

    public byte _byte(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Byte)
            return (Byte) o;
        throwConversionError("Can't convert '" + o.getClass() + "' to byte");
        return 0;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException
    {
        return _short(getObject(columnIndex));
    }

    @Override
    public short getShort(String columnName) throws SQLException
    {
        return _short(getObject(columnName));
    }

    public short _short(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Short || o instanceof Byte)
            return ((Number) o).shortValue();
        throwConversionError("Can't convert '" + o.getClass() + "' to short");
        return 0;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException
    {
        return _int(getObject(columnIndex));
    }

    @Override
    public int getInt(String columnName) throws SQLException
    {
        return _int(getObject(columnName));
    }

    public int _int(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Integer || o instanceof Short || o instanceof Byte)
            return ((Number) o).intValue();
        if (o instanceof Long)
        {
            long l = (Long) o;
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
                return (int)l;
        }
        throwConversionError("Can't convert '" + o.getClass() + "' to int");
        return 0;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException
    {
        return _long(getObject(columnIndex));
    }

    @Override
    public long getLong(String columnName) throws SQLException
    {
        return _long(getObject(columnName));
    }

    public long _long(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Long || o instanceof Short || o instanceof Byte)
            return ((Number) o).longValue();
        throwConversionError("Can't convert '" + o.getClass() + "' to long");
        return 0;
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException
    {
        return _float(getObject(columnIndex));
    }

    @Override
    public float getFloat(String columnName) throws SQLException
    {
        return _float(getObject(columnName));
    }

    public float _float(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Number)
            return ((Number) o).floatValue();
        throwConversionError("Can't convert '" + o.getClass() + "' to float");
        return 0;
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException
    {
        return _double(getObject(columnIndex));
    }

    @Override
    public double getDouble(String columnName) throws SQLException
    {
        return _double(getObject(columnName));
    }

    private double _double(Object o) throws SQLException
    {
        if (null == o)
            return 0;
        if (o instanceof Number)
        {
            double value = ((Number) o).doubleValue();
            return ResultSetUtil.mapDatabaseDoubleToJavaDouble(value);
        }
        throwConversionError("Can't convert '" + o.getClass() + "' to double");
        return 0;
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
    {
        return _decimal(getObject(columnIndex), scale);
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException
    {
        return _decimal(getObject(columnName), scale);
    }

    public BigDecimal _decimal(Object o, int scale) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof BigDecimal)
        {
            BigDecimal d = (BigDecimal) o;
            if (d.scale() == scale)
                return d;
            return d.setScale(scale);
        }
        throwConversionError("Can't convert '" + o.getClass() + "' to BigDecimal");
        return null;
    }

    public BigDecimal _decimal(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof BigDecimal)
        {
            return (BigDecimal) o;
        }
        throwConversionError("Can't convert '" + o.getClass() + "' to BigDecimal");
        return null;
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException
    {
        return _bytes(getObject(columnIndex));
    }

    @Override
    public byte[] getBytes(String columnName) throws SQLException
    {
        return _bytes(getObject(columnName));
    }

    public byte[] _bytes(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof byte[])
            return (byte[]) o;
        if (o instanceof Blob)
        {
            long length = ((Blob)o).length();
            if (length > Integer.MAX_VALUE)
                throwConversionError("Blob too long: " + length);
            return ((Blob)o).getBytes(1, (int)length);
        }
        throwConversionError("Can't convert '" + o.getClass() + "' to byte[]");
        return null;
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException
    {
        return _date(getObject(columnIndex));
    }

    @Override
    public Date getDate(String columnName) throws SQLException
    {
        return _date(getObject(columnName));
    }

    public java.sql.Date _date(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof java.sql.Date)
            return (java.sql.Date) o;
        if (o instanceof Timestamp)
            return new java.sql.Date(((Timestamp)o).getTime());
        throwConversionError("Can't convert '" + o.getClass() + "' to java.sql.Date");
        return null;
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException
    {
        return _time(getObject(columnIndex));
    }

    @Override
    public Time getTime(String columnName) throws SQLException
    {
        return _time(getObject(columnName));
    }

    public Time _time(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof Time)
            return (Time) o;
        throwConversionError("Can't convert '" + o.getClass() + "' to java.sql.Time");
        return null;
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException
    {
        return _timestamp(getObject(columnIndex));
    }

    @Override
    public Timestamp getTimestamp(String columnName) throws SQLException
    {
        return _timestamp(getObject(columnName));
    }

    public Timestamp _timestamp(Object o) throws SQLException
    {
        if (null == o)
            return null;
        if (o instanceof Timestamp)
            return (Timestamp) o;
        if (o instanceof java.util.Date)
            return new Timestamp(((java.util.Date) o).getTime());
        if (o instanceof Long)
            return new Timestamp((Long) o);
        throwConversionError("Can't convert '" + o.getClass() + "' to java.sql.Timestamp");
        return null;
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Override
    public InputStream getAsciiStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Override
    public InputStream getUnicodeStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException
    {
        return (InputStream) throwNYI();
    }

    @Override
    public InputStream getBinaryStream(String columnName) throws SQLException
    {
        return (InputStream) throwNYI();
    }


    @Override
    public SQLWarning getWarnings() throws SQLException
    {
        return (SQLWarning) throwNYI();
    }

    @Override
    public void clearWarnings()
    {
    }

    @Override
    public String getCursorName() throws SQLException
    {
        return (String) throwNYI();
    }

    @Override
    public ResultSetMetaData getMetaData()
    {
        return _md;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException
    {
        if (_row < 0 || _row >= _rowMaps.size())
            throw new SQLException("No current row");

        _lastObject = _rowMaps.get(_row).get(columnIndex);

        if (_lastObject instanceof Double)
            _lastObject = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) _lastObject);

        return _lastObject;
    }

    @Override
    public Object getObject(String columnName) throws SQLException
    {
        _lastObject = _rowMaps.get(_row).get(columnName);
        // check for no illegal column name
        if (_lastObject == null)
            findColumn(columnName);

        if (_lastObject instanceof Double)
            _lastObject = ResultSetUtil.mapDatabaseDoubleToJavaDouble((Double) _lastObject);

        return _lastObject;
    }


    // careful! this does no error checking
    public void _setObject(int columnIndex, Object o)
    {
        _rowMaps.get(_row).set(columnIndex, o);
    }


    @Override
    public int findColumn(String columnLabel) throws SQLException
    {
        Integer i = _columns.get(columnLabel.toLowerCase());
        if (null == i)
            throw new SQLException("No such column: " + columnLabel);
        return i;
    }


    @Override
    public Reader getCharacterStream(int columnIndex)
    {
        return null;
    }

    @Override
    public Reader getCharacterStream(String columnName)
    {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException
    {
        return _decimal(getObject(columnIndex));
    }

    @Override
    public BigDecimal getBigDecimal(String columnName) throws SQLException
    {
        return _decimal(getObject(columnName));
    }

    @Override
    public boolean isBeforeFirst()
    {
        return _row == -1;
    }

    @Override
    public boolean isAfterLast()
    {
        return _row == _rowMaps.size();
    }

    @Override
    public boolean isFirst()
    {
        return _rowMaps.size() > 0 && _row == 0;
    }

    @Override
    public boolean isLast()
    {
        return _rowMaps.size() > 0 && _row == _rowMaps.size() - 1;
    }

    @Override
    public void beforeFirst()
    {
        _row = -1;
    }

    @Override
    public void afterLast()
    {
        _row = _rowMaps.size();
    }

    @Override
    protected void finalize() throws Throwable
    {
        if (!_wasClosed)
        {
            close();

            if (_requireClose && AppProps.getInstance().isDevMode())
            {
                StringBuilder error = new StringBuilder("CachedResultSet was not closed.");
                if (null != _url)
                    error.append("\nURL: ").append(_url);
                else if (_threadName != null)
                    error.append("\nthreadName: ").append(_threadName);
                error.append("\nStack trace from the creation:");
                error.append(ExceptionUtil.renderStackTrace(_stackTrace));

                _log.error(error);
            }
        }
        super.finalize();
    }

    @Override
    public boolean first()
    {
        return absolute(1);
    }

    @Override
    public boolean last()
    {
        return absolute(-1);
    }

    @Override
    public int getRow()
    {
        // adjust to 1-based
        return _row >= 0 && _row < _rowMaps.size() ? _row + 1 : 0;
    }

    @Override
    public boolean absolute(int row)
    {
        if (row >= 0)
            beforeFirst();
        else
            afterLast();
        return relative(row);
    }

    @Override
    public boolean relative(int rows)
    {
        _row = max(-1, min(_rowMaps.size(), _row + rows));
        return getRow() != 0;
    }

    @Override
    public boolean previous()
    {
        return relative(-1 * _direction);
    }

    @Override
    public boolean rowUpdated()
    {
        return false;
    }

    @Override
    public boolean rowInserted()
    {
        return false;
    }

    @Override
    public boolean rowDeleted()
    {
        return false;
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scale) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateNull(String columnName) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBoolean(String columnName, boolean x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateByte(String columnName, byte x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateShort(String columnName, short x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateInt(String columnName, int x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateLong(String columnName, long x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateFloat(String columnName, float x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateDouble(String columnName, double x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateString(String columnName, String x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBytes(String columnName, byte[] x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateDate(String columnName, Date x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateTime(String columnName, Time x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateTimestamp(String columnName, Timestamp x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader, int length) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateObject(String columnName, Object x, int scale) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateObject(String columnName, Object x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void insertRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void deleteRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void refreshRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void cancelRowUpdates() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void moveToInsertRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public void moveToCurrentRow() throws SQLException
    {
        throwNYI();
    }

    @Override
    public Statement getStatement()
    {
        return null;   // C
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException
    {
        return throwNYI();
    }

    @Override
    public Ref getRef(int i) throws SQLException
    {
        return (Ref) throwNYI();
    }

    @Override
    public Blob getBlob(int i) throws SQLException
    {
        return (Blob) throwNYI();
    }

    @Override
    public Clob getClob(int i) throws SQLException
    {
        return (Clob) throwNYI();
    }

    @Override
    public Array getArray(int i) throws SQLException
    {
        return (Array) throwNYI();
    }

    @Override
    public Object getObject(String colName, Map<String, Class<?>> map) throws SQLException
    {
        return throwNYI();
    }

    @Override
    public Ref getRef(String colName) throws SQLException
    {
        return (Ref) throwNYI();
    }

    @Override
    public Blob getBlob(String colName) throws SQLException
    {
        return (Blob) throwNYI();
    }

    @Override
    public Clob getClob(String colName) throws SQLException
    {
        return (Clob) throwNYI();
    }

    @Override
    public Array getArray(String colName) throws SQLException
    {
        return (Array) throwNYI();
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException
    {
        return (Date) throwNYI();
    }

    @Override
    public Date getDate(String columnName, Calendar cal) throws SQLException
    {
        return (Date) throwNYI();
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException
    {
        return (Time) throwNYI();
    }

    @Override
    public Time getTime(String columnName, Calendar cal) throws SQLException
    {
        return (Time) throwNYI();
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
    {
        return (Timestamp) throwNYI();
    }

    @Override
    public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException
    {
        return (Timestamp) throwNYI();
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException
    {
        return (URL) throwNYI();
    }

    @Override
    public URL getURL(String columnName) throws SQLException
    {
        return (URL) throwNYI();
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateRef(String columnName, Ref x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateBlob(String columnName, Blob x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateClob(String columnName, Clob x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException
    {
        throwNYI();
    }

    @Override
    public void updateArray(String columnName, Array x) throws SQLException
    {
        throwNYI();
    }

    //
    // Table.TableResultSet
    //

    @Override
    public boolean isComplete()
    {
        return _isComplete;
    }

    @Override
    public String getTruncationMessage(int maxRows)
    {
        return "Displaying only the first " + maxRows + " rows.";
    }

    @Override
    public Map<String, Object> getRowMap() throws SQLException
    {
        if (_row >= _rowMaps.size())
            throw new SQLException("No current row");
        return _rowMaps.get(_row);
    }

    @Override
    @NotNull
    public Iterator<Map<String, Object>> iterator()
    {
        Iterator<Map<String, Object>> it = new RowMapIterator(_rowMaps.iterator());

        return IteratorUtils.unmodifiableIterator(it);
    }

    private static class RowMapIterator implements Iterator<Map<String, Object>>
    {
        private final Iterator<RowMap<Object>> _iter;

        private RowMapIterator(Iterator<RowMap<Object>> iter)
        {
            _iter = iter;
        }

        @Override
        public boolean hasNext()
        {
            return _iter.hasNext();
        }

        @Override
        public Map<String, Object> next()
        {
            return _iter.next();
        }

        @Override
        public void remove()
        {
            _iter.remove();
        }
    }

    @Override
    public int getSize()
    {
        return _rowMaps.size();
    }

    //
    // helpers
    //

    private static Object throwNYI() throws SQLException
    {
        throw new SQLException("NYI");
    }


    private static Object throwConversionError(String msg) throws SQLException
    {
        throw new SQLException(msg);
    }


    // The following methods are "implemented" to allow compiling and running on JDK/JRE 6.0 while still supporting
    // JDK/JRE 5.0.  If/when we require JDK/JRE 6.0, these methods should delegate to the wrapped resultset.


    @Override
    public boolean isWrapperFor(Class<?> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldability()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
    {
        return _wasClosed;
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNString(int columnIndex, String nString)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNString(String columnLabel, String nString)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(int columnIndex, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(String columnLabel, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    // JDBC 4.1 methods below must be here so we compile on JDK 7; implement once we require JRE 7.

    @Override
    public <T> T getObject(int columnIndex, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class DataIteratorAdapter implements DataIterator
    {
        @Override
        public String getDebugName()
        {
            return "CachedResultSet.DataIteratorAdapter";
        }

        @Override
        public int getColumnCount()
        {
            return 0;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return null;
        }

        @Override
        public boolean next()
        {
            return false;
        }

        @Override
        public Object get(int i)
        {
            return null;
        }

        @Override
        public void close()
        {
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
    }
}
