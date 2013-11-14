package org.labkey.query.olap;


import org.json.JSONObject;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.olap4j.AllocationPolicy;
import org.olap4j.Axis;
import org.olap4j.Cell;
import org.olap4j.CellSet;
import org.olap4j.CellSetAxis;
import org.olap4j.CellSetAxisMetaData;
import org.olap4j.CellSetMetaData;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.Position;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.Property;

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
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 10/19/13
 * Time: 8:58 AM
 *
 * a Qube is a wrapper for a Cube or Cube like structure. Each level in the cube will generate a map of sets.
 *
 * The level map is a collection of Member->MemberSet entries where the MemberSet is the collection of key Members that
 * have this 'attribute'.
 *
 * For instance, the key attribute level might be [Participant].[Participant], and the Gender level may have a few members such as
 * [Gender].[Male], [Gender].[Female], [Gender].[Unknown]
 *
 * Each member will have an associated set of participant members.
 *
 * [Gender].[Male] -> {[Participant].[P001], [Participant].[P002]}
 * [Gender].[Female] -> {[Participant].[P003], [Participant].[P004]}
 * [Gender].[Unknown] -> {[Participant].[P005]}
 */

public class Qube
{
    OlapConnection _connection;
    Cube _cube;
    Level _keyLevel;

    static Cache _cache = CacheManager.getStringKeyCache(10000,CacheManager.HOUR,"Qube Member Cache");
    MemberSet _templateKeyMemberSet;


    public Qube(OlapConnection connection, Cube cube, Level keyLevel) throws OlapException
    {
        _cube = cube;
        _keyLevel = keyLevel;
        _templateKeyMemberSet = new MemberSet(keyLevel.getMembers());
    }


    public CellSet executeQuery(JSONObject json)
    {
        return new _CellSet();
    }


    public CellSet executeQuery(QubeQuery expr)
    {
        return new _CellSet();
    }


    public class _CellSet implements CellSet
    {
        // COLUMN,ROWS,PAGES,CHAPTERS,SECTIONS
        List<CellSetAxis> _axes = new ArrayList<>(3);
        int _columnCount = 1;
        CellSetAxis _filterAxis;
        Double[] _results;

        _CellSet()
        {

        }

        _CellSet(List<CellSetAxis> axes)
        {
            _axes.addAll(axes);
            _columnCount = axes.get(0).getPositionCount();
        }

        _CellSet(List<CellSetAxis> axes, Double[] results)
        {
            _axes.addAll(axes);
            _columnCount = axes.get(0).getPositionCount();
            _results = results.clone();
        }

        void setAxis(int i, CellSetAxis axis)
        {
            _axes.set(i, axis);
        }

        void setAxis(int i, Level l) throws OlapException
        {
            if (i<0 || i>1)
                throw new UnsupportedOperationException();
            Axis axis = i==0 ? Axis.COLUMNS: Axis.ROWS;
            setAxis(i, new _LevelCellSetAxis(this, axis, l));
        }

        @Override
        public OlapStatement getStatement() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CellSetMetaData getMetaData() throws OlapException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CellSetAxis> getAxes()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public CellSetAxis getFilterAxis()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Cell getCell(List<Integer> integers)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Cell getCell(int i)
        {
            return new _Cell(i);
        }

        @Override
        public Cell getCell(Position... positions)
        {
            int ordinal = 0;
            int multiplier = 1;
            for (int i=0 ; i<positions.length ; i++)
            {
                ordinal += positions[i].getOrdinal() * multiplier;
                multiplier *= _axes.get(i).getPositionCount();
            }
            return new _Cell(ordinal);
        }

        @Override
        public List<Integer> ordinalToCoordinates(int ordinal)
        {
            if (1 == _axes.size())
                return Collections.singletonList(ordinal);
            if (2 == _axes.size())
                return Arrays.asList(ordinal / _columnCount, ordinal % _columnCount);
            else
                throw new UnsupportedOperationException("only support rows/columns");
        }

        @Override
        public int coordinatesToOrdinal(List<Integer> integers)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean next() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean wasNull() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getString(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte getByte(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getDouble(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getBytes(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getDate(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Time getTime(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Timestamp getTimestamp(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getAsciiStream(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getUnicodeStream(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getBinaryStream(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getString(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte getByte(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getDouble(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getBytes(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getDate(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Time getTime(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Timestamp getTimestamp(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getAsciiStream(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getUnicodeStream(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getBinaryStream(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearWarnings() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCursorName() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getObject(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getObject(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int findColumn(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader getCharacterStream(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader getCharacterStream(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isBeforeFirst() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAfterLast() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFirst() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLast() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void beforeFirst() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void afterLast() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean first() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean last() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean absolute(int row) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean relative(int rows) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean previous() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFetchDirection(int direction) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getFetchDirection() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFetchSize(int rows) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getFetchSize() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getType() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getConcurrency() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean rowUpdated() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean rowInserted() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean rowDeleted() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNull(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBoolean(int columnIndex, boolean x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateByte(int columnIndex, byte x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateShort(int columnIndex, short x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateInt(int columnIndex, int x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLong(int columnIndex, long x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateFloat(int columnIndex, float x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDouble(int columnIndex, double x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateString(int columnIndex, String x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBytes(int columnIndex, byte[] x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDate(int columnIndex, Date x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTime(int columnIndex, Time x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateObject(int columnIndex, Object x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNull(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBoolean(String columnLabel, boolean x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateByte(String columnLabel, byte x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateShort(String columnLabel, short x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateInt(String columnLabel, int x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLong(String columnLabel, long x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateFloat(String columnLabel, float x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDouble(String columnLabel, double x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateString(String columnLabel, String x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBytes(String columnLabel, byte[] x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDate(String columnLabel, Date x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTime(String columnLabel, Time x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateObject(String columnLabel, Object x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refreshRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelRowUpdates() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveToInsertRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveToCurrentRow() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ref getRef(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Blob getBlob(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Clob getClob(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Array getArray(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ref getRef(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Blob getBlob(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Clob getClob(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Array getArray(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getDate(int columnIndex, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Date getDate(String columnLabel, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Time getTime(int columnIndex, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Time getTime(String columnLabel, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL getURL(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL getURL(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRef(int columnIndex, Ref x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRef(String columnLabel, Ref x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(int columnIndex, Blob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(String columnLabel, Blob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(int columnIndex, Clob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(String columnLabel, Clob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateArray(int columnIndex, Array x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateArray(String columnLabel, Array x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RowId getRowId(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RowId getRowId(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRowId(int columnIndex, RowId x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRowId(String columnLabel, RowId x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getHoldability() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClosed() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNString(int columnIndex, String nString) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNString(String columnLabel, String nString) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(int columnIndex, NClob nClob) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(String columnLabel, NClob nClob) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public NClob getNClob(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public NClob getNClob(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLXML getSQLXML(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLXML getSQLXML(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getNString(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getNString(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader getNCharacterStream(int columnIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader getNCharacterStream(String columnLabel) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(String columnLabel, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(int columnIndex, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateClob(String columnLabel, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(int columnIndex, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateNClob(String columnLabel, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException
        {
            throw new UnsupportedOperationException();
        }


        public class _Cell implements Cell
        {
            int _ordinal;

            _Cell(int i)
            {
                _ordinal = i;
            }

            @Override
            public CellSet getCellSet()
            {
                return _CellSet.this;
            }

            @Override
            public int getOrdinal()
            {
                return _ordinal;
            }

            @Override
            public List<Integer> getCoordinateList()
            {
                return ordinalToCoordinates(_ordinal);
            }

            @Override
            public Object getPropertyValue(Property property)
            {
                return null;
            }

            @Override
            public boolean isEmpty()
            {
                return null==_results[_ordinal];
            }

            @Override
            public boolean isError()
            {
                return false;
            }

            @Override
            public boolean isNull()
            {
                return null==_results[_ordinal];
            }

            @Override
            public double getDoubleValue() throws OlapException
            {
                Double d = _results[_ordinal];
                return null==d ? 0.0 : d;
            }

            @Override
            public String getErrorText()
            {
                return null;
            }

            @Override
            public Object getValue()
            {
                return _results[_ordinal];
            }

            @Override
            public String getFormattedValue()
            {
                Double d = _results[_ordinal];
                return null==d ? "" : String.valueOf(d);
            }

            @Override
            public ResultSet drillThrough() throws OlapException
            {
                throw new UnsupportedOperationException("drillThrough not supported");
            }

            @Override
            public void setValue(Object o, AllocationPolicy allocationPolicy, Object... objects) throws OlapException
            {
                throw new UnsupportedOperationException("drillThrough not supported");
            }
        }
    }

    abstract public class _CellSetAxis implements CellSetAxis
    {
        final Axis _axis;
        final CellSet _cellset;
        ArrayList<Position> _positions;

        _CellSetAxis(CellSet cellset, Axis axis)
        {
            _cellset = cellset;
            _axis = axis;
        }

        @Override
        public Axis getAxisOrdinal()
        {
            return _axis;
        }

        @Override
        public CellSet getCellSet()
        {
            return _cellset;
        }

        @Override
        public CellSetAxisMetaData getAxisMetaData()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Position> getPositions()
        {
            return Collections.unmodifiableList(_positions);
        }

        @Override
        public int getPositionCount()
        {
            return _positions.size();
        }

        @Override
        public ListIterator<Position> iterator()
        {
            return (Collections.unmodifiableList(_positions)).listIterator();
        }
    }


    public class _LevelCellSetAxis extends _CellSetAxis
    {
        _LevelCellSetAxis(CellSet cellset, Axis axis, Level level) throws OlapException
        {
            super(cellset, axis);
            List<Member> members = level.getMembers();
            ArrayList<Position> positions = new ArrayList<>(members.size());
            for (Member m : members)
            {
                Position p = new _Position(positions.size(), m);
                positions.add(p);
            }
            _positions = positions;
        }
    }

    public class _HierarchyCellSetAxis extends _CellSetAxis
    {
        _HierarchyCellSetAxis(CellSet cellset, Axis axis, Hierarchy hierarchy) throws OlapException
        {
            super(cellset, axis);
            int size = 0;
            for (Level l : hierarchy.getLevels())
                size += l.getCardinality();
            ArrayList<Position> positions = new ArrayList<>(size);
            for (Level l : hierarchy.getLevels())
            {
                for (Member m : l.getMembers())
                {
                    Position p = new _Position(positions.size(), m);
                    positions.add(p);
                }
            }
            _positions = positions;
        }
    }


    public class _Position implements Position
    {
        int _ordinal;
        Member _member;

        _Position(int ordinal, Member member)
        {
            _ordinal = ordinal;
            _member = member;
        }

        @Override
        public List<Member> getMembers()
        {
            return Collections.singletonList(_member);
        }

        @Override
        public int getOrdinal()
        {
            return _ordinal;
        }
    }


}