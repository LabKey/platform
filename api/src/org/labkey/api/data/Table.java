/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.PropertyUtils;
import static org.apache.commons.lang.StringUtils.stripEnd;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.Join;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.util.Pair;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.CachedRowSet;
import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * Table manipulation methods
 * <p/>
 * select/insert/update/delete
 */

public class Table
{
    public static Set<String> ALL_COLUMNS = Collections.unmodifiableSet(Collections.<String>emptySet());

    /** Return all rows instead of limiting to the top n */
    public static final int ALL_ROWS = 0;
    public static final int NO_ROWS = -2;

    public static String SQLSTATE_TRANSACTION_STATE = "25000";
    public static final int ERROR_ROWVERSION = 10001;
    public static final int ERROR_DELETED = 10002;

    private static Logger _log = Logger.getLogger(Table.class);

    private Table()
    {
    }


    // Careful: caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static PreparedStatement prepareStatement(Connection conn, String sql, Object[] parameters)
            throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(sql);
        setParameters(stmt, parameters);
        assert MemTracker.put(stmt);
        return stmt;
    }


    private static ResultSet _executeQuery(Connection conn, String sql, Object[] parameters) throws SQLException
    {
        return _executeQuery(conn, sql, parameters, null, false, null);
    }

    private static ResultSet _executeQuery(Connection conn, String sql, Object[] parameters, AsyncQueryRequest asyncRequest, boolean scrollable, Integer statementRowCount)
            throws SQLException
    {
        ResultSet rs;

        if (null == parameters || 0 == parameters.length)
        {
            Statement statement = conn.createStatement(scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            if (null != statementRowCount)
            {
                statement.setMaxRows(statementRowCount.intValue() == NO_ROWS ? 0 : statementRowCount.intValue());
            }
            if (asyncRequest != null)
            {
                asyncRequest.setStatement(statement);
            }
            rs = statement.executeQuery(sql);
        }
        else
        {
            PreparedStatement stmt = conn.prepareStatement(sql, scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            if (null != statementRowCount)
            {
                stmt.setMaxRows(statementRowCount.intValue());
            }
            if (asyncRequest != null)
            {
                asyncRequest.setStatement(stmt);
            }
            try
            {
                setParameters(stmt, parameters);
                rs = stmt.executeQuery();
            }
            finally
            {
                closeParameters(parameters);
            }
        }
        if (asyncRequest != null)
        {
            asyncRequest.setStatement(null);
        }

        assert MemTracker.put(rs);
        return rs;
    }


    public static int execute(Connection conn, String sql, Object[] parameters)
            throws SQLException
    {
        Statement stmt = null;

        try
        {
            if (null == parameters || 0 == parameters.length)
            {
                stmt = conn.createStatement();
                if (stmt.execute(sql))
                    return -1;
                else
                    return stmt.getUpdateCount();
            }
            else
            {
                stmt = conn.prepareStatement(sql);
                setParameters((PreparedStatement)stmt, parameters);
                if (((PreparedStatement)stmt).execute())
                    return -1;
                else
                    return stmt.getUpdateCount();
            }
        }
        finally
        {
            if (null != stmt)
                stmt.close();

            if (null != parameters)
                closeParameters(parameters);
        }
    }

    private static void setParameters(PreparedStatement stmt, Object[] parameters) throws SQLException
    {
        setParameters(stmt, Arrays.asList(parameters));
    }

    public static void setParameters(PreparedStatement stmt, Collection<?> parameters) throws SQLException
    {
        if (null == parameters)
            return;

        int i = 0;

        for (Object p : parameters)
        {
            // UNDONE: this code belongs in Parameter._bind()
            //Parameter validation
            //Bug 1996 - rossb:  Generally, we let JDBC validate the
            //parameters and throw exceptions, however, it doesn't recognize NaN
            //properly which can lead to database corruption.  Trap that here
            {
                //if the input parameter is NaN, throw a sql exception
                boolean isInvalid = false;
                if (p instanceof Float)
                {
                    isInvalid = p.equals(Float.NaN);
                }
                else if (p instanceof Double)
                {
                    isInvalid = p.equals(Double.NaN);
                    if (!isInvalid)
                        p = ResultSetUtil.mapJavaDoubleToDatabaseDouble(((Double) p).doubleValue());
                }

                if (isInvalid)
                {
                    throw new SQLException("Illegal argument (" + Integer.toString(i) + ") to SQL Statement:  " + p.toString() + " is not a valid parameter");
                }
            }

            Parameter.bindObject(stmt, i + 1, p);
            i++;
        }
    }

    public static void closeParameters(Object[] parameters)
    {
        for (Object value : parameters)
        {
            if (value instanceof AttachmentFile)
            {
                try
                {
                    ((AttachmentFile)value).closeInputStream();
                }
                catch(IOException e)
                {
                    // Ignore... make sure we attempt to close all the parameters
                }
            }
        }
    }

    public static Table.TableResultSet executeQuery(DbSchema schema, String sql, Object[] parameters)
            throws SQLException
    {
        return (Table.TableResultSet) executeQuery(schema, sql, parameters, Table.ALL_ROWS, true);
    }

    public static Table.TableResultSet executeQuery(DbSchema schema, SQLFragment sql) throws SQLException
    {
        return executeQuery(schema, sql.getSQL(), sql.getParams().toArray());
    }


    public static Table.TableResultSet executeQuery(DbSchema schema, SQLFragment sql, int rowCount)
            throws SQLException
    {
        return (Table.TableResultSet) executeQuery(schema, sql.getSQL(), sql.getParamsArray(), rowCount, 0, true, false, null, null, null);
    }

    public static ResultSet executeQuery(DbSchema schema, SQLFragment sql, int rowCount, boolean cache, boolean scrollable) throws SQLException
    {
        return executeQuery(schema, sql.getSQL(), sql.getParamsArray(), rowCount, cache, scrollable);
    }

    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, boolean cache)
            throws SQLException
    {
        return executeQuery(schema, sql, parameters, rowCount, 0, cache, false, null, null, null);
    }

    public static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, boolean cache, boolean scrollable)
            throws SQLException
    {
        return executeQuery(schema, sql, parameters, rowCount, 0, cache, scrollable, null, null, null);
    }

    private static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, long scrollOffset, boolean cache, boolean scrollable, AsyncQueryRequest asyncRequest, Logger log, Integer statementRowCount)
            throws SQLException
    {
        if (log == null) log = _log;
        Connection conn = null;
        ResultSet rs = null;
        boolean queryFailed = false;

        try
        {
            conn = schema.getScope().getConnection(log);
            rs = _executeQuery(conn, sql, parameters, asyncRequest, scrollable, statementRowCount);

            while (scrollOffset > 0 && rs.next())
                scrollOffset--;

            if (cache)
                return cacheResultSet(rs, rowCount);
            else
                return new ResultSetImpl(conn, schema, rs, rowCount);
        }
        catch(SQLException e)
        {
            // For every SQLException log error, query SQL, and params, then throw
            _log.error("internalExecuteQuery", e);
            _logQuery(Priority.ERROR, sql, parameters, conn);
            queryFailed = true;
            throw(e);
        }
        finally
        {
            // Close everything for cached result sets and exceptions only
            if (cache || queryFailed)
                _doFinally(rs, null, conn, schema);
        }
    }


    public static <K> K[] executeQuery(DbSchema schema, SQLFragment sqlf, Class<K> clss)
            throws SQLException
    {
        return internalExecuteQueryArray(schema, sqlf.getSQL(), sqlf.getParamsArray(), clss, 0);
    }


    public static <K> K[] executeQuery(DbSchema schema, String sql, Object[] parameters, Class<K> clss)
            throws SQLException
    {
        return internalExecuteQueryArray(schema, sql, parameters, clss, 0);
    }

    @NotNull
    private static <K> K[] internalExecuteQueryArray(DbSchema schema, String sql, Object[] parameters, Class<K> clss, long scrollOffset)
            throws SQLException
    {
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            rs = _executeQuery(conn, sql, parameters);

            while (scrollOffset > 0 && rs.next())
                scrollOffset--;

            ObjectFactory<K> f = ObjectFactory.Registry.getFactory(clss);
            if (null != f)
            {
                return f.handleArray(rs);
            }

            if (clss == java.util.Map.class)
            {
                CachedRowSetImpl copy = (CachedRowSetImpl)cacheResultSet(rs, 0);
                //noinspection unchecked
                K[] arrayListMaps = (K[])(copy._arrayListMaps == null ? new ArrayListMap[0] : copy._arrayListMaps);
                copy.close();
                return arrayListMaps;
            }

            throw new java.lang.IllegalArgumentException("could not create requested class");
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(rs, null, conn, schema);
        }
    }


    public static int execute(DbSchema schema, SQLFragment f)
            throws SQLException
    {
        return execute(schema, f.getSQL(), f.getParamsArray());
    }


    public static int execute(DbSchema schema, String sql, Object[] parameters)
            throws SQLException
    {
        Connection conn = schema.getScope().getConnection();

        try
        {
            return execute(conn, sql, parameters);
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(null, null, conn, schema);
        }
    }


    // Careful: Caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static void batchExecute(DbSchema schema, String sql, Collection<? extends Collection<?>> paramList)
            throws SQLException
    {
        Connection conn = schema.getScope().getConnection();
        PreparedStatement stmt = null;

        try
        {
            stmt = conn.prepareStatement(sql);
            int paramCounter = 0;
            for (Collection<?> params : paramList)
            {
                setParameters(stmt, params);
                stmt.addBatch();

                paramCounter += params.size();
                if (paramCounter > 1000)
                {
                    paramCounter = 0;
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        catch (SQLException e)
        {
            if (e instanceof BatchUpdateException)
            {
                if (null != e.getNextException())
                    e = e.getNextException();
            }

            _doCatch(sql, null, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(null, stmt, conn, schema);
        }
    }


    /**
     * return a result from a one row one column resultset or null.
     * K should be a string or number type.
     */
    public static <K> K executeSingleton(TableInfo table, String column, Filter filter, Sort sort, Class<K> c)
    {
        ColumnInfo col = table.getColumn(column);
        return executeSingleton(table, col, filter, sort, c);
    }

    /**
     * return a result from a one row one column resultset or null.
     * K should be a string or number type.
     */
    // TODO: why is there a sort parameter here?
    public static <K> K executeSingleton(TableInfo table, ColumnInfo column, Filter filter, Sort sort, Class<K> c)
    {
        try
        {
            K[] values = executeArray(table, column, filter, sort, c);
            assert (values.length == 0 || values.length == 1);
            if (values.length == 0)
                return null;

            return values[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    /**
     * return a result from a one row one column resultset
     * does not distinguish between not found, and set NULL
     */
    public static <K> K executeSingleton(DbSchema schema, String sql, Object[] parameters, Class<K> c) throws SQLException
    {
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            rs = _executeQuery(conn, sql, parameters);
            if (!rs.next())
                return null;

            return Getter.getByClass(rs, c);
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw e;
        }
        finally
        {
            _doFinally(rs, null, conn, schema);
        }
    }


    private static Map<Class, Getter> _getterMap = new HashMap<Class, Getter>(10);

    private static enum Getter
    {
        STRING(String.class) { String getObject(ResultSet rs) throws SQLException { return rs.getString(1); }},
        INTEGER(Integer.class) { Integer getObject(ResultSet rs) throws SQLException { int i = rs.getInt(1); return rs.wasNull() ? null : i ; }},
        BOOLEAN(Boolean.class) { Boolean getObject(ResultSet rs) throws SQLException { boolean f = rs.getBoolean(1); return rs.wasNull() ? null : f ; }},
        LONG(Long.class) { Long getObject(ResultSet rs) throws SQLException { long l = rs.getLong(1); return rs.wasNull() ? null : l; }},
        UTIL_DATE(Date.class) { Date getObject(ResultSet rs) throws SQLException { return rs.getTimestamp(1); }},
        BYTES(byte[].class) { Object getObject(ResultSet rs) throws SQLException { return rs.getBytes(1); }},
        TIMESTAMP(Timestamp.class) { Object getObject(ResultSet rs) throws SQLException { return rs.getTimestamp(1); }};

        abstract Object getObject(ResultSet rs) throws SQLException;

        private Getter(Class c)
        {
            _getterMap.put(c, this);
        }

        public static <K> K getByClass(ResultSet rs, Class<K> c) throws SQLException
        {
            Getter getter = forClass(c);

            if (null == getter)
                throw new IllegalArgumentException("Class " + c.getName() + " is not supported by Getter.getByClass()");

            //noinspection unchecked
            return (K)getter.getObject(rs);
        }

        public static <K> Getter forClass(Class<K> c)
        {
            return _getterMap.get(c);
        }
    }


    // Standard SQLException catch block: log exception, query SQL, and params
    private static void _doCatch(String sql, Object[] parameters, Connection conn, SQLException e)
    {
        if (sql.startsWith("INSERT") && SqlDialect.isConstraintException(e))
        {
            _log.warn("SQL Exception", e);
            _logQuery(Priority.WARN, sql, parameters, conn);
        }
        else
        {
            _log.error("SQL Exception", e);
            _logQuery(Priority.ERROR, sql, parameters, conn);

        }
    }


    // Typical finally block cleanup
    private static void _doFinally(ResultSet rs, Statement stmt, Connection conn, DbSchema schema)
    {
        try
        {
            if (stmt == null && rs != null)
                stmt = rs.getStatement();
        }
        catch (SQLException x)
        {
            _log.error("_doFinally", x);
        }

        try
        {
            if (null != rs) rs.close();
        }
        catch (SQLException x)
        {
            _log.error("_doFinally", x);
        }
        try
        {
            if (null != stmt) stmt.close();
        }
        catch (SQLException x)
        {
            _log.error("_doFinally", x);
        }
        try
        {
            if (null != conn) schema.getScope().releaseConnection(conn);
        }
        catch (SQLException x)
        {
            _log.error("_doFinally", x);
        }
    }


    /** return a result from a one column resultset. K should be a string or number type */
    public static <K> K[] executeArray(TableInfo table, String column, Filter filter, Sort sort, Class<K> c)
            throws SQLException
    {
        ColumnInfo col = table.getColumn(column);
        return executeArray(table, col, filter, sort, c);
    }

    /** return a result from a one column resultset. K should be a string or number type */
    public static <K> K[] executeArray(TableInfo table, ColumnInfo col, Filter filter, Sort sort, Class<K> c)
            throws SQLException
    {
        Map<String,ColumnInfo> cols = new CaseInsensitiveHashMap<ColumnInfo>();
        cols.put(col.getName(), col);
        if (filter != null || sort != null)
            ensureRequiredColumns(table, cols, filter, sort);
        SQLFragment sqlf = getSelectSQL(table, cols.values(), filter, sort);

        DbSchema schema = table.getSchema();
        String sql = sqlf.getSQL();
        Object[] parameters = sqlf.getParams().toArray();

        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            rs = _executeQuery(conn, sql, parameters);
            ArrayList<Object> list = new ArrayList<Object>();
            int i = rs.findColumn(col.getAlias());
            while (rs.next())
                list.add(rs.getObject(i));
            return list.toArray((K[]) Array.newInstance(c, list.size()));
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(rs, null, conn, schema);
        }
    }
    
    /** return a result from a one column resultset. K should be a string or number type */
    public static <K> K[] executeArray(DbSchema schema, SQLFragment sql, Class<K> c)
            throws SQLException
    {
        return executeArray(schema, sql.getSQL(), sql.getParams().toArray(), c);
    }

    /** return a result from a one column resultset. K should be a string or number type */
    public static <K> K[] executeArray(DbSchema schema, String sql, Object[] parameters, Class<K> c)
            throws SQLException
    {
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            rs = _executeQuery(conn, sql, parameters);
            ArrayList<Object> list = new ArrayList<Object>();
            while (rs.next())
                list.add(rs.getObject(1));
            return list.toArray((K[]) Array.newInstance(c, list.size()));
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(rs, null, conn, schema);
        }
    }


    /**
     * This is a shortcut method which can be used for TWO column ResultSets
     * The first column is key, the second column is the value
     */
    public static Map executeValueMap(DbSchema schema, String sql, Object[] parameters, Map<Object,Object> m)
            throws SQLException
    {
        Connection conn = null;
        ResultSet rs = null;

        try
        {
            conn = schema.getScope().getConnection();
            rs = _executeQuery(conn, sql, parameters);
            if (null == m)
                m = new HashMap<Object,Object>();
            while (rs.next())
                m.put(rs.getObject(1), rs.getObject(2));
            return m;
        }
        catch(SQLException e)
        {
            _doCatch(sql, parameters, conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(rs, null, conn, schema);
        }
    }


    /**
     * return a 'clean' list of fields to update
     */
    protected static <K> Map<String, Object> _getTableData(TableInfo table, K from, boolean insert)
    {
        Map<String, Object> fields;
        //noinspection unchecked
        ObjectFactory<K> f = ObjectFactory.Registry.getFactory((Class<K>)from.getClass());
        if (null == f)
            throw new IllegalArgumentException("Cound not find a matching object factory.");
        fields = f.toMap(from, null);
        return _getTableData(table, fields, insert);
    }


    protected static Map<String, Object> _getTableData(TableInfo table, Map<String,Object> fields, boolean insert)
    {
        if (!(fields instanceof CaseInsensitiveHashMap))
            fields = new CaseInsensitiveHashMap<Object>(fields);

        // special rename case
        if (fields.containsKey("containerId"))
            fields.put("container", fields.get("containerId"));

        List<ColumnInfo> columns = table.getColumns();
        Map<String, Object> m = new CaseInsensitiveHashMap<Object>(columns.size() * 2);

        for (ColumnInfo column : columns)
        {
            String key = column.getName();

//            if (column.isReadOnly() && !(insert && key.equals("EntityId")))
            if (!insert && column.isReadOnly() || column.isAutoIncrement() || column.isVersionColumn())
                continue;

            if (!fields.containsKey(key))
            {
                if (Character.isUpperCase(key.charAt(0)))
                {
                    key = Character.toLowerCase(key.charAt(0)) + key.substring(1);
                    if (!fields.containsKey(key))
                        continue;
                }
                else
                    continue;
            }

            Object v = fields.get(key);
            if (v instanceof String)
                v = _trimRight((String) v);
            m.put(column.getName(), v);
        }
        return m;
    }


    static String _trimRight(String s)
    {
        if (null == s) return "";
        return stripEnd(s, "\t\r\n ");
    }


    protected static void _insertSpecialFields(User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo col = table.getColumn("Owner");
        if (null != col && null != user)
            fields.put("Owner", user.getUserId());
        col = table.getColumn("CreatedBy");
        if (null != col && null != user)
            fields.put("CreatedBy", user.getUserId());
        col = table.getColumn("Created");
        if (null != col)
        {
            Date dateCreated = (Date)fields.get("Created");
            if (null == dateCreated || 0 == dateCreated.getTime())
                fields.put("Created", date);
        }
        col = table.getColumn("EntityId");
        if (col != null && fields.get("EntityId") == null)
            fields.put("EntityId", GUID.makeGUID());
    }

    protected static void _copyInsertSpecialFields(Object returnObject, Map<String, Object> fields)
    {
        if (returnObject == fields)
            return;

        // make sure that any GUID generated in this routine is stored in the returned object
        if (fields.containsKey("EntityId"))
            _setProperty(returnObject, "EntityId", fields.get("EntityId"));
        if (fields.containsKey("Owner"))
            _setProperty(returnObject, "Owner", fields.get("Owner"));
        if (fields.containsKey("Created"))
            _setProperty(returnObject, "Created", fields.get("Created"));
        if (fields.containsKey("CreatedBy"))
            _setProperty(returnObject, "CreatedBy", fields.get("CreatedBy"));
    }

    protected static void _updateSpecialFields(User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo colModifiedBy = table.getColumn("ModifiedBy");
        if (null != colModifiedBy && null != user)
            fields.put(colModifiedBy.getName(), user.getUserId());

        ColumnInfo colModified = table.getColumn("Modified");
        if (null != colModified)
            fields.put(colModified.getName(), date);

        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && colVersion != colModified && colVersion.getSqlTypeInt() == Types.TIMESTAMP)
            fields.put(colVersion.getName(), date);
    }

    protected static void _copyUpdateSpecialFields(TableInfo table, Object returnObject, Map<String, Object> fields)
    {
        if (returnObject == fields)
            return;

        if (fields.containsKey("ModifiedBy"))
            _setProperty(returnObject, "ModifiedBy", fields.get("ModifiedBy"));

        if (fields.containsKey("Modified"))
            _setProperty(returnObject, "Modified", fields.get("Modified"));

        ColumnInfo colModified = table.getColumn("Modified");
        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && colVersion != colModified && colVersion.getSqlTypeInt() == Types.TIMESTAMP)
            _setProperty(returnObject, colVersion.getName(), fields.get(colVersion.getName()));
    }


    static private void _setProperty(Object fields, String propName, Object value)
    {
        if (fields instanceof Map)
        {
            ((Map<String,Object>) fields).put(propName, value);
        }
        else
        {
            if (Character.isUpperCase(propName.charAt(0)))
                propName = Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
            try
            {
                if (PropertyUtils.isWriteable(fields, propName))
                {
                    PropertyUtils.setProperty(fields, propName, value);
                }
                // UNDONE: horrible postgres hack..., not general fix
                else if (propName.endsWith("id"))
                {
                    propName = propName.substring(0,propName.length()-2) + "Id";
                    if (PropertyUtils.isWriteable(fields, propName))
                        PropertyUtils.setProperty(fields, propName, value);
                }
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }
    }


    // Returns a new Map<String, Object> if fieldsIn is a Map, otherwise returns modified version of fieldsIn.
    public static <K> K insert(User user, TableInfo table, K fieldsIn) throws SQLException
    {
        assert (table.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB): ("Table " + table.getSchema().getName() + "." + table.getName() + " is not in the physical database.");

        // _executeTriggers(table, fields);

        StringBuilder columnSQL = new StringBuilder();
        StringBuilder valueSQL = new StringBuilder();
        ArrayList<Object> parameters = new ArrayList<Object>();
        ColumnInfo autoIncColumn = null;
        String comma = "";

        //noinspection unchecked
        Map<String, Object> fields = fieldsIn instanceof Map ?
                _getTableData(table, (Map<String, Object>)fieldsIn, true) :
                _getTableData(table, fieldsIn, true);
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        _insertSpecialFields(user, table, fields, date);
        _updateSpecialFields(user, table, fields, date);

        List<ColumnInfo> columns = table.getColumns();
        for (ColumnInfo column : columns)
        {
            if (column.isAutoIncrement())
                autoIncColumn = column;

            if (!fields.containsKey(column.getName()))
                continue;

            Object value = fields.get(column.getName());
            columnSQL.append(comma);
            columnSQL.append(column.getSelectName());
            valueSQL.append(comma);
            if (null == value || value instanceof String && 0 == ((String) value).length())
                valueSQL.append("NULL");
            else
            {
                valueSQL.append('?');
                parameters.add(value);
            }
            comma = ", ";
        }

        if (comma.length() == 0)
        {
            // NO COLUMNS TO INSERT
            throw new IllegalArgumentException("Table.insert called with no column data. table=" + table + " object=" + String.valueOf(fieldsIn));
        }

        StringBuilder insertSQL = new StringBuilder("INSERT INTO ");
        insertSQL.append(table.getSelectName());
        insertSQL.append("\n\t(");
        insertSQL.append(columnSQL);
        insertSQL.append(")\n\t");
        insertSQL.append("VALUES (");
        insertSQL.append(valueSQL);
        insertSQL.append(')');

        if (null != autoIncColumn)
            table.getSqlDialect().appendSelectAutoIncrement(insertSQL, table, autoIncColumn.getSelectName());

        // If Map was handed in, then we hand back a Map
        // UNDONE: use Table.select() to reselect and return new Object

        //noinspection unchecked
        K returnObject = (K) (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap) ? fields : fieldsIn);

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            conn = schema.getScope().getConnection();

            stmt = prepareStatement(conn, insertSQL.toString(), parameters.toArray());
            stmt.execute();

            if (null != autoIncColumn)
                if (stmt.getMoreResults())
                {
                    rs = stmt.getResultSet();
                    rs.next();

                    // Explicitly retrieve the new rowId based on the autoIncrement type.  We shouldn't use getObject()
                    // here because PostgreSQL sequences always return Long, and we expect Integer in many places.
                    if (autoIncColumn.getJavaClass().isAssignableFrom(Long.TYPE))
                        _setProperty(returnObject, autoIncColumn.getName(), rs.getLong(1));
                    else
                        _setProperty(returnObject, autoIncColumn.getName(), rs.getInt(1));
                }
            _copyInsertSpecialFields(returnObject, fields);
            _copyUpdateSpecialFields(table, returnObject, fields);

            notifyTableUpdate(table);
        }
        catch(SQLException e)
        {
            _doCatch(insertSQL.toString(), parameters.toArray(), conn, e);
            throw(e);
        }
        finally
        {
            _doFinally(rs, stmt, conn, schema);
            closeParameters(parameters.toArray());
        }

        return returnObject;
    }


    public static <K> K update(User user, TableInfo table, K fieldsIn, Object rowId)
            throws SQLException
    {
        assert (table.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB): (table.getName() + " is not in the physical database.");
        assert null != rowId;

        // _executeTriggers(table, previous, fields);

        StringBuilder setSQL = new StringBuilder();
        StringBuilder whereSQL = new StringBuilder();
        ArrayList<Object> parametersSet = new ArrayList<Object>();
        ArrayList<Object> parametersWhere = new ArrayList<Object>();
        String comma = "";
        String whereAND = "WHERE ";

        // NOTE: no multi-column primary keys?
        // UNDONE -- rowVersion
        List<ColumnInfo> columnPK = table.getPkColumns();
        Object[] pkVals;

        assert null != columnPK;
        assert columnPK.size() == 1 || ((Object[]) rowId).length == columnPK.size();

        if (columnPK.size() == 1 && !rowId.getClass().isArray())
            pkVals = new Object[]{rowId};
        else
            pkVals = (Object[]) rowId;

        for (int i = 0; i < columnPK.size(); i++)
        {
            whereSQL.append(whereAND);
            whereSQL.append(columnPK.get(i).getSelectName());
            whereSQL.append("=?");
            parametersWhere.add(pkVals[i]);
            whereAND = " AND ";
        }

        //noinspection unchecked
        Map<String, Object> fields = fieldsIn instanceof Map ?
            _getTableData(table, (Map<String,Object>)fieldsIn, true) :
            _getTableData(table, fieldsIn, true);
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        _updateSpecialFields(user, table, fields, date);

        List<ColumnInfo> columns = table.getColumns();

        for (ColumnInfo column : columns)
        {
            if (!fields.containsKey(column.getName()))
                continue;

            Object value = fields.get(column.getName());
            setSQL.append(comma);
            setSQL.append(column.getSelectName());

            if (null == value || value instanceof String && 0 == ((String) value).length())
            {
                setSQL.append("=NULL");
            }
            else
            {
                setSQL.append("=?");
                parametersSet.add(value);
            }

            comma = ", ";
        }

        // UNDONE: reselect
        String updateSQL = "UPDATE " + table.getSelectName() + "\n\t" +
                "SET " + setSQL + "\n\t" +
                whereSQL;

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;

        ArrayList<Object> parameterList = new ArrayList<Object>(parametersSet.size() + parametersWhere.size());
        parameterList.addAll(parametersSet);
        parameterList.addAll(parametersWhere);
        Object[] parameters = parameterList.toArray();

        try
        {
            conn = schema.getScope().getConnection();
            int count = execute(conn, updateSQL, parameters);

            // check for concurrency problem
            if (count == 0)
            {
                throw OptimisticConflictException.create(ERROR_DELETED);
            }

            _copyUpdateSpecialFields(table, fieldsIn, fields);
            notifyTableUpdate(table);
        }
        catch(SQLException e)
        {
            _doCatch(updateSQL, parameters, conn, e);
            throw(e);
        }

        finally
        {
            _doFinally(null, stmt, conn, schema);
        }

        return (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap)) ? (K)fields : fieldsIn;
    }


    public static void delete(TableInfo table, Object rowId)
            throws SQLException
    {
        List<ColumnInfo> columnPK = table.getPkColumns();
        Object[] pkVals;

        assert null != columnPK;
        assert columnPK.size() == 1 || ((Object[]) rowId).length == columnPK.size();

        if (columnPK.size() == 1 && !rowId.getClass().isArray())
            pkVals = new Object[]{rowId};
        else
            pkVals = (Object[]) rowId;

        SimpleFilter filter = new SimpleFilter();
        for (int i = 0; i < pkVals.length; i++)
            filter.addCondition(columnPK.get(i), pkVals[i]);

        // UNDONE -- rowVersion
        if (delete(table, filter) == 0)
        {
            throw OptimisticConflictException.create(ERROR_DELETED);
        }
    }


    public static int delete(TableInfo table, Filter filter)
            throws SQLException
    {
        assert (table.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB): (table.getName() + " is not in the physical database.");

        SQLFragment where = filter.getSQLFragment(table, null);

        String deleteSQL = "DELETE FROM " + table.getSelectName() + "\n\t" + where.getSQL();
        int result = Table.execute(table.getSchema(), deleteSQL, where.getParams().toArray());

        notifyTableUpdate(table);
        return result;
    }


    public static <K> K selectObject(TableInfo table, int pk, Class<K> clss)
    {
        return selectObject(table, new Object[]{pk}, clss);
    }


    public static <K> K selectObject(TableInfo table, Object pk, Class<K> clss)
    {
        SimpleFilter filter = new SimpleFilter();
        List<ColumnInfo> pkColumns = table.getPkColumns();
        Object[] pks;

        if (null != pk && pk.getClass().isArray())
            pks = (Object[]) pk;
        else
            pks = new Object[]{pk};

        assert pks.length == pkColumns.size() : "Wrong number of primary keys specified";

        for (int i = 0; i < pkColumns.size(); i++)
            filter.addCondition(pkColumns.get(i), pks[i]);

        try
        {
            K[] values = select(table, Table.ALL_COLUMNS, filter, null, clss);
            assert (values.length == 0 || values.length == 1);

            if (values.length == 0)
                return null;

            return values[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public static Map<String, Object>[] selectMaps(TableInfo table, Set<String> select, Filter filter, Sort sort) throws SQLException
    {
        //noinspection unchecked
        return (Map<String, Object>[]) select(table, select, filter, sort, Map.class);
    }


    public static <K> K selectObject(TableInfo table, Filter filter, Sort sort, Class<K> clss) throws SQLException
    {
        return selectObject(table, ALL_COLUMNS, filter, sort, clss);
    }


    public static <K> K selectObject(TableInfo table, Set<String> select, Filter filter, Sort sort, Class<K> clss)
            throws SQLException
    {
        K[] results = select(table, select, filter, sort, clss);
        if (results.length == 0)
            return null;
        return results[0];
    }


    static private int decideRowCount(int rowcount, Class clazz)
    {
        if (Table.ALL_ROWS == rowcount || Table.NO_ROWS == rowcount)
            return rowcount;

        // add 1 to count so we can set isComplete()
        if (null == clazz || java.sql.ResultSet.class.isAssignableFrom(clazz))
            return rowcount + 1;
        return rowcount;
    }


    public static SQLFragment getFullSelectSQL(TableInfo table, List<ColumnInfo> select, Filter filter, Sort sort)
    {
        List<ColumnInfo> allColumns = new ArrayList<ColumnInfo>(select);
        return getSelectSQL(table, allColumns, filter, sort, 0, 0);
    }


    public static SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort)
    {
        return getSelectSQL(table, columns, filter, sort, 0, 0);
    }


    /**
     * Returns the sql for a select.
     */
    public static SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, int rowCount, long offset)
    {
		return QueryService.get().getSelectSQL(table, columns, filter, sort, rowCount, offset);
    }


    public static ResultSet select(TableInfo table, Set<String> select, Filter filter, Sort sort)
            throws SQLException
    {
        return select(table, columnInfosList(table, select), filter, sort);
    }


    public static TableResultSet select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort)
            throws SQLException
    {
        SQLFragment sql = getSelectSQL(table, columns, filter, sort);
        return (TableResultSet) executeQuery(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), Table.ALL_ROWS, true);
    }


    @NotNull
    public static <K> K[] select(TableInfo table, Set<String> select, Filter filter, Sort sort, Class<K> clss)
            throws SQLException
    {
        return select(table, select, filter, sort, clss, 0, 0);
    }


    @NotNull
    public static <K> K[] select(TableInfo table, Set<String> select, Filter filter, Sort sort, Class<K> clss, int rowCount, long offset)
            throws SQLException
    {
        return select(table, columnInfosList(table, select), filter, sort, clss, rowCount, offset);
    }


    @NotNull
    public static <K> K[] select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Class<K> clss)
            throws SQLException
    {
        return select(table, columns, filter, sort, clss, 0, 0);
    }

    @NotNull
    public static <K> K[] select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort, Class<K> clss, int rowCount, long offset)
            throws SQLException
    {
        long queryOffset = offset, scrollOffset = 0;
        int queryRowCount = rowCount;
        if (offset > 0 && !table.getSqlDialect().supportsOffset())
        {
            queryOffset = 0;
            scrollOffset = offset;
            queryRowCount = rowCount + (int)offset;
        }

        SQLFragment sql = getSelectSQL(table, columns, filter, sort, queryRowCount, queryOffset);   // TODO: Use decideRowCount() here?
        return internalExecuteQueryArray(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), clss, scrollOffset);
    }


    public static TableResultSet selectForDisplay(TableInfo table, Set<String> select, Filter filter, Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, columnInfosList(table, select), filter, sort, rowCount, offset);
    }


    public static TableResultSet selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Filter filter, Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, select, filter, sort, rowCount, offset, true);
    }

    public static Map<String, Aggregate.Result>selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates, Collection<ColumnInfo> select, Filter filter, boolean cache) throws SQLException
    {
        return selectAggregatesForDisplay(table, aggregates, select, filter, cache, null);
    }

    private static Map<String, Aggregate.Result> selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates, Collection<ColumnInfo> select, Filter filter, boolean cache, AsyncQueryRequest asyncRequest)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, null);
        SQLFragment innerSql = getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, null, 0, 0);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        boolean first = true;
        for (Aggregate agg : aggregates)
        {
            if (agg.isCountStar() || columns.containsKey(agg.getColumnName()))
            {
                if (first)
                    first = false;
                else
                    sql.append(", ");
                sql.append(agg.getSQL());
            }
        }
        Map<String, Aggregate.Result> results = new HashMap<String, Aggregate.Result>();

        // if we didn't find any columns, then skip the SQL call completely
        if (first)
            return results;

        sql.append(" FROM (").append(innerSql.getSQL()).append(") S");

        Table.TableResultSet rs = null;
        try
        {
            rs = (Table.TableResultSet) executeQuery(table.getSchema(), sql.toString(), innerSql.getParams().toArray(), 0, 0, cache, false, asyncRequest, null, null);
            boolean next = rs.next();
            if (!next)
                throw new IllegalStateException("Expected a non-empty resultset from aggregate query.");
            for (Aggregate agg : aggregates)
                results.put(agg.getColumnName(), agg.getResult(rs));
            return results;
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {_log.error("unexpected error", e);}
        }
    }

    public static Map<String, Aggregate.Result> selectAggregatesForDisplayAsync(final TableInfo table, final List<Aggregate> aggregates, final Collection<ColumnInfo> select, final Filter filter, final boolean cache, HttpServletResponse response)
            throws SQLException, IOException
    {
        final AsyncQueryRequest<Map<String, Aggregate.Result>> asyncRequest = new AsyncQueryRequest<Map<String, Aggregate.Result>>(response);
        return asyncRequest.waitForResult(new Callable<Map<String, Aggregate.Result>>() {
            public Map<String, Aggregate.Result> call() throws Exception
            {
                return selectAggregatesForDisplay(table, aggregates, select, filter, cache, asyncRequest);
            }
        });
    }


    public static TableResultSet selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Filter filter, Sort sort, int rowCount, long offset, boolean cache)
            throws SQLException
    {
        return selectForDisplay(table, select, filter, sort, rowCount, offset, cache, null, null);
    }


    private static TableResultSet selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Filter filter, Sort sort, int rowCount, long offset, boolean cache, AsyncQueryRequest asyncRequest, Logger log)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, sort);

        long queryOffset = offset, scrollOffset = 0;
        int queryRowCount = rowCount;
        if (offset > 0 && !table.getSqlDialect().supportsOffset())
        {
            queryOffset = 0;
            scrollOffset = offset;
            queryRowCount = rowCount + (int)offset;
        }

        int decideRowCount = decideRowCount(queryRowCount, null);
        SQLFragment sql = getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, decideRowCount, queryOffset);
        Integer statementRowCount = (table.getSqlDialect().requiresStatementMaxRows() ? decideRowCount : null);  // TODO: clean this all up
        return (Table.TableResultSet)executeQuery(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), rowCount, scrollOffset, cache, !cache, asyncRequest, log, statementRowCount);
    }


    public static TableResultSet selectForDisplayAsync(final TableInfo table, final Collection<ColumnInfo> select, final Filter filter, final Sort sort, final int rowCount, final long offset, final boolean cache, HttpServletResponse response) throws SQLException, IOException
    {
        final Logger log = ConnectionWrapper.getConnectionLogger();
        final AsyncQueryRequest<TableResultSet> asyncRequest = new AsyncQueryRequest<TableResultSet>(response);
        return asyncRequest.waitForResult(new Callable<TableResultSet>()
		{
            public TableResultSet call() throws Exception
            {
                return selectForDisplay(table, select, filter, sort, rowCount, offset, cache, asyncRequest, log);
            }
        });
    }


    public static <K> K[] selectForDisplay(TableInfo table, Set<String> select, Filter filter, Sort sort, Class<K> clss)
            throws SQLException
    {
        return selectForDisplay(table, columnInfosList(table, select), filter, sort, clss);
    }


    public static <K> K[] selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Filter filter, Sort sort, Class<K> clss)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, sort);
        SQLFragment sql = getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, 0, 0);
        return internalExecuteQueryArray(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), clss, 0);
    }


    private static List<ColumnInfo> columnInfosList(TableInfo table, Set<String> select)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        List<ColumnInfo> selectColumns;

        if (select == ALL_COLUMNS)
            selectColumns = allColumns;
        else
        {
            select = new CaseInsensitiveHashSet(select);
            List<ColumnInfo> selectList = new ArrayList<ColumnInfo>();      // TODO: Just use selectColumns
            for (ColumnInfo column : allColumns)
            {
                if (select != ALL_COLUMNS && !select.contains(column.getName()) && !select.contains(column.getPropertyName()))
                    continue;
                selectList.add(column);
            }
            selectColumns = selectList;
        }
        return selectColumns;
    }


    private static Map<String,ColumnInfo> getDisplayColumnsList(Collection<ColumnInfo> arrColumns)
    {
        Map<String, ColumnInfo> columns = new LinkedHashMap<String, ColumnInfo>();
        ColumnInfo existing;
        for (ColumnInfo column : arrColumns)
        {
            existing = columns.get(column.getAlias());
            assert null == existing || existing.getName().equals(column.getName()) : existing.getName() + " != " + column.getName();
            columns.put(column.getAlias(), column);
            ColumnInfo displayColumn = column.getDisplayField();
            if (displayColumn != null)
            {
                existing = columns.get(displayColumn.getAlias());
                assert null == existing || existing.getName().equals(displayColumn.getName());
                columns.put(displayColumn.getAlias(), displayColumn);
            }
        }
        return columns;
    }


    public static void ensureRequiredColumns(TableInfo table, Map<String, ColumnInfo> cols, Filter filter, Sort sort)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        Set<String> requiredColumns = new CaseInsensitiveHashSet();

        if (null != filter)
            requiredColumns.addAll(filter.getWhereParamNames());

        if (null != sort)
        {
            requiredColumns.addAll(sort.getRequiredColumnNames(cols));
        }

        // TODO: Ensure pk, filter & where columns in cases where caller is naive

        for (ColumnInfo column : allColumns)
        {
            if (cols.containsKey(column.getAlias()))
                continue;
            if (requiredColumns.contains(column.getAlias()) || requiredColumns.contains(column.getPropertyName()))
                cols.put(column.getAlias(), column);
            else if (column.isKeyField())
                cols.put(column.getAlias(), column);
            else if (column.isVersionColumn())
                cols.put(column.getAlias(), column);
        }
    }


    public static class TempTableInfo extends SchemaTableInfo
    {
        TempTableTracker _ttt;
        String _tempTableName;
        String _unique;

        static private String shortGuid()
        {
            String guid = GUID.makeGUID();
            StringBuilder sb = new StringBuilder(guid.length());
            for (int i=0 ; i<guid.length() ; i++)
            {
                char ch = guid.charAt(i);
                if (ch != '-')
                    sb.append(ch);
            }
            return sb.toString();
        }

        public TempTableInfo(DbSchema parentSchema, String name, List<ColumnInfo> cols, List<String> pk)
        {
            super(parentSchema);

            _unique = shortGuid();
            _tempTableName = parentSchema.getSqlDialect().getGlobalTempTablePrefix() + name + "$" + _unique;

            // overwrite selectName to not use schema/owner name
            this.name = name;
            selectName = new SQLFragment(_tempTableName);
            for (ColumnInfo col : cols)
                col.setParentTable(this);
            columns.addAll(cols);
            if (pk != null)
                setPkColumnNames(pk);
            setTableType(TABLE_TYPE_TABLE);
        }

        public void setButtonBarConfig(ButtonBarConfig bbarConfig)
        {
            _buttonBarConfig = bbarConfig;
        }

        public String getSelectName()
        {
            return _tempTableName;
        }


        public String getTempTableName()
        {
            return _tempTableName;
        }


        /** Call this method when table is physically created */
        public void track()
        {
            _ttt = TempTableTracker.track(getSchema(), getTempTableName(), this);
        }


        public void delete()
        {
            _ttt.delete();
        }

        public boolean verify()
        {
            try
            {
                isEmpty(this);
                return true;
            }
            catch (SQLException e)
            {
                return false;
            }
        }
    }


    static public TempTableInfo createTempTable(TableInfo tinfo, String name) throws SQLException
    {
        //
        // create TableInfo
        //

        ArrayList<ColumnInfo> cols = new ArrayList<ColumnInfo>();
        for (ColumnInfo col : tinfo.getColumns())
        {
            ColumnInfo colDirect = new ColumnInfo(col.getName());
            colDirect.setAlias(col.getAlias());
            colDirect.copyAttributesFrom(col);
            colDirect.copyURLFrom(col, null, null);
            cols.add(colDirect);
        }

        TempTableInfo tinfoTempTable = new TempTableInfo(tinfo.getSchema(), name, cols, tinfo.getPkColumnNames());
        String tempTableName = tinfoTempTable.getTempTableName();

        //
        // Track the table, it will be deleted when tinfoTempTable is GC'd
        //

        tinfoTempTable.track();

        //
        // create table
        //

        snapshot(tinfo, tempTableName);

        return tinfoTempTable;
    }


    public static void snapshot(TableInfo tinfo, String tableName)
            throws SQLException
    {
        SQLFragment sqlSelect = Table.getSelectSQL(tinfo, null, null, null);
        SQLFragment sqlSelectInto = new SQLFragment();
        sqlSelectInto.append("SELECT * INTO ").append(tableName).append(" FROM (");
        sqlSelectInto.append(sqlSelect);
        sqlSelectInto.append(") _from_");

        Table.execute(tinfo.getSchema(), sqlSelectInto);
    }


    public static boolean isEmpty(TableInfo tinfo) throws SQLException
    {
        return rowCount(tinfo) == 0;
    }


    public static long rowCount(TableInfo tinfo) throws SQLException
    {
        return Table.executeSingleton(tinfo.getSchema(), "SELECT COUNT(*) FROM " + tinfo, null, Long.class);
    }


    private static TableResultSet cacheResultSet(ResultSet rs, int rowCount) throws SQLException
    {
        return new CachedRowSetImpl(rs, rowCount);
    }


    public interface TableResultSet extends ResultSet
    {
        public boolean isComplete();

        public Map<String, Object> getRowMap() throws SQLException;

        public Iterator<Map<String, Object>> iterator();

        String getTruncationMessage(int maxRows);
    }



    public static class ResultSetImpl extends ResultSetWrapper implements TableResultSet
    {
        private final DbSchema schema;
        private final Connection connection;
        private boolean isComplete = true;
        private int maxRows = 0;

        // for resource tracking
        private Throwable debugCreated = null;
        protected boolean wasClosed = false;


        public ResultSetImpl(ResultSet rs)
        {
            this(null, null, rs, 0);
        }


        public ResultSetImpl(Connection connection, DbSchema schema, ResultSet rs)
        {
            this(connection, schema, rs, 0);
        }

        public ResultSetImpl(Connection connection, DbSchema schema, ResultSet rs, int maxRows)
        {
            super(rs);
            assert MemTracker.put(this);
            //noinspection ConstantConditions
            assert null != (debugCreated = new Throwable("created ResultSetImpl"));
            this.maxRows = maxRows;
            this.connection = connection;
            this.schema = schema;
        }


        public void setMaxRows(int i)
        {
            maxRows = i;
        }


        public boolean isComplete()
        {
            return isComplete;
        }


        void setComplete(boolean isComplete)
        {
            this.isComplete = isComplete;
        }


        public boolean next() throws SQLException
        {
            boolean success = super.next();
            if (!success || 0 == maxRows)
                return success;
            return this.getRow() <= maxRows;
        }


        public void close() throws SQLException
        {
            // Uncached case... close everything down
            if (null != schema)
            {
                Statement stmt = getStatement();
                super.close();
                if (stmt != null)
                {
                    stmt.close();
                }
                schema.getScope().releaseConnection(connection);
            }
            else
                super.close();

            wasClosed = true;
        }


        public int size() throws SQLException
        {
            if (resultset instanceof CachedRowSet)
                return ((CachedRowSet) resultset).size();
            return -1;
        }


        public Iterator<Map<String, Object>> iterator()
        {
            return new ResultSetIterator(this);
        }

        public String getTruncationMessage(int maxRows)
        {
            return "Displaying only the first " + maxRows + " rows.";
        }

        public Map<String, Object> getRowMap()
        {
            throw new java.lang.UnsupportedOperationException("getRowMap()");
        }


        protected void finalize() throws Throwable
        {
            if (!wasClosed)
            {
                close();
                if (null != debugCreated)
                    _log.error("ResultSet was not closed", debugCreated);
            }
            super.finalize();
        }
    }


    public static class OptimisticConflictException extends SQLException
    {
        public OptimisticConflictException(String errorMessage, String sqlState, int error)
        {
            super(errorMessage, sqlState, error);
        }


        public static OptimisticConflictException create(int error)
        {
            switch (error)
            {
                case Table.ERROR_DELETED:
                    return new OptimisticConflictException("Optimistic concurrency exception: Row deleted",
                            Table.SQLSTATE_TRANSACTION_STATE,
                            error);
                case Table.ERROR_ROWVERSION:
                    return new OptimisticConflictException("Optimistic concurrency exception: Row updated",
                            Table.SQLSTATE_TRANSACTION_STATE,
                            error);
            }
            assert false : "unexpected error code";
            return null;
        }
    }



    // Table modification

    public static void notifyTableUpdate(/*String operation,*/ TableInfo table/*, Container c*/)
    {
        DbCache.invalidateAll(table);
    }


    private static void _logQuery(Priority pri, String sql, Object[] parameters, Connection conn)
    {
        if (!_log.isEnabledFor(pri))
            return;

        StringBuilder logEntry = new StringBuilder(sql.length() * 2);
        logEntry.append("SQL ");

        Integer sid = null;
        if (conn instanceof ConnectionWrapper)
            sid = ((ConnectionWrapper)conn).getSPID();
        if (sid != null)
            logEntry.append(" [").append(sid).append("]");

        String[] lines = sql.split("\n");
        for (String line : lines)
            logEntry.append("\n    ").append(line);

        for (int i = 0; null != parameters && i < parameters.length; i++)
            logEntry.append("\n    ?[").append(i + 1).append("] ").append(String.valueOf(parameters[i]));

        logEntry.append("\n");
        _appendTableStackTrace(logEntry, 5);
        _log.log(pri, logEntry);
    }


    static void _appendTableStackTrace(StringBuilder sb, int count)
    {
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        int i=1;  // Always skip call to getStackTrace()
        for ( ; i<ste.length ; i++)
        {
            String line = ste[i].toString();
            if (!line.startsWith("org.labkey.api.data.Table."))
                break;
        }
        int last = Math.min(ste.length,i+count);
        for ( ; i<last ; i++)
        {
            String line = ste[i].toString();
            if (line.startsWith("javax.servelet.http.HttpServlet.service("))
                break;
            sb.append("\n\t").append(line);
        }
    }

    
    /**
     * perform an in-memory join between the provided collection and a SQL table.
     * This is designed to work efficiently when the number of unique values of 'key'
     * is relatively small compared to left.size()
     *
     * @param left is the input collection
     * @param key  is the name of the column to join in the input collection
     * @param sql  is a string of the form "SELECT key, a, b FROM table where col in (?)"
     */
    public static List<Map> join(List<Map> left, String key, DbSchema schema, String sql) // NYI , Map right)
            throws SQLException
    {
        TreeSet<Object> keys = new TreeSet<Object>();
        for (Map m : left)
            keys.add(m.get(key));
        int size = keys.size();
        if (size == 0)
            return Collections.unmodifiableList(left);

        int q = sql.indexOf('?');
        if (q == -1 || sql.indexOf('?', q + 1) != -1)
            throw new IllegalArgumentException("malformed SQL for join()");
        StringBuilder inSQL = new StringBuilder(sql.length() + size * 2);
        inSQL.append(sql.substring(0, q + 1));
        for (int i = 1; i < size; i++)
            inSQL.append(",?");
        inSQL.append(sql.substring(q + 1));

        Map[] right = internalExecuteQueryArray(schema, inSQL.toString(), keys.toArray(), Map.class, 0);
        return Join.join(left, Arrays.asList(right), key);
    }


    public static class TestCase extends junit.framework.TestCase
    {
        static private CoreSchema _core = CoreSchema.getInstance();

        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        static public class Principal
        {
            int userId;
            String ownerId;
            String type;
            String name;


            public int getUserId()
            {
                return userId;
            }


            public void setUserId(int userId)
            {
                this.userId = userId;
            }


            public String getOwnerId()
            {
                return ownerId;
            }


            public void setOwnerId(String ownerId)
            {
                this.ownerId = ownerId;
            }


            public String getType()
            {
                return type;
            }


            public void setType(String type)
            {
                this.type = type;
            }


            public String getName()
            {
                return name;
            }


            public void setName(String name)
            {
                this.name = name;
            }
        }


        public void testSelect() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            ResultSet rs = Table.select(tinfo, Table.ALL_COLUMNS, null, null);
            rs.close();

            Map[] maps = Table.select(tinfo, Table.ALL_COLUMNS, null, null, Map.class);
            assertNotNull(maps);

            Principal[] principals = Table.select(tinfo, Table.ALL_COLUMNS, null, null, Principal.class);
            assertNotNull(principals);
            assertTrue(principals.length > 0);
            assertTrue(principals[0].userId != 0);
            assertNotNull(principals[0].name);
        }


        public void testMaxRows() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            TableResultSet rsAll = Table.selectForDisplay(tinfo, Table.ALL_COLUMNS, null, null, 0, 0);
            rsAll.last();
            int rowCount = rsAll.getRow();
            assertTrue(rsAll.isComplete());
            rsAll.close();

            rowCount -= 2;
            TableResultSet rs = Table.selectForDisplay(tinfo, Table.ALL_COLUMNS, null, null, rowCount, 0);
            rs.last();
            int row = rs.getRow();
            assertTrue(row == rowCount);
            assertFalse(rs.isComplete());
            rs.close();
        }


        public void testMapJoin()
        {
            ArrayList<Map> left = new ArrayList<Map>();
            left.add(_quickMap("id=1&A=1"));
            left.add(_quickMap("id=2&A=2"));
            left.add(_quickMap("id=3&A=3"));
            left.add(_quickMap("id=4&A=1"));
            left.add(_quickMap("id=5&A=2"));
            left.add(_quickMap("id=6&A=3"));
            ArrayList<Map> right = new ArrayList<Map>();
            right.add(_quickMap("id=HIDDEN&A=1&B=one"));
            right.add(_quickMap("id=HIDDEN&A=2&B=two"));
            right.add(_quickMap("id=HIDDEN&A=3&B=three"));

            Collection<Map> join = Join.join(left, right, "A");
            Set<String> idSet = new HashSet<String>();
            for (Map m : join)
            {
                idSet.add((String)m.get("id"));
                assertNotSame(m.get("id"), "HIDDEN");
                assertTrue(!m.get("A").equals("1") || m.get("B").equals("one"));
                assertTrue(!m.get("A").equals("2") || m.get("B").equals("two"));
                assertTrue(!m.get("A").equals("3") || m.get("B").equals("three"));
                PageFlowUtil.toQueryString(m.entrySet());
            }
            assertEquals(idSet.size(), 6);
        }


        public void testSqlJoin()
                throws SQLException
        {
            //UNDONE
            // SELECT MEMBERS
            // Join(MEMBERS, "SELECT * FROM Principals where UserId IN (?)"

            CoreSchema core = CoreSchema.getInstance();
            DbSchema schema = core.getSchema();
            TableInfo membersTable = core.getTableInfoMembers();
            TableInfo principalsTable = core.getTableInfoPrincipals();

            Map[] members = executeQuery(schema, "SELECT * FROM " + membersTable.getSelectName(), null, Map.class);
            List<Map> users = join(Arrays.asList(members), "UserId", schema,
                    "SELECT * FROM " + principalsTable.getSelectName() + " WHERE UserId IN (?)");
            for (Map m : users)
            {
                String s = PageFlowUtil.toQueryString(m.entrySet());
            }
        }


        enum MyEnum
        {
            FRED, BARNEY, WILMA, BETTY
        }

        public void testParameter()
                throws Exception
        {
            DbSchema core = DbSchema.get("core");
            SqlDialect dialect = core.getScope().getSqlDialect();
            
            String name = dialect.getTempTablePrefix() + "_" + GUID.makeHash();
            Connection conn = core.getScope().getConnection();
            assertTrue(conn != null);
            
            try
            {
                PreparedStatement stmt = conn.prepareStatement("CREATE " + dialect.getTempTableKeyword() + " TABLE " + name +
                        "(s VARCHAR(36), d " + dialect.sqlTypeNameFromSqlType(Types.TIMESTAMP) + ")");
                stmt.execute();
                stmt.close();

                String sql = "INSERT INTO " + name + " VALUES (?, ?)";
                stmt = conn.prepareStatement(sql);
                Parameter.bindObject(stmt, 1, 4);
                Parameter.bindObject(stmt, 2, GregorianCalendar.getInstance());
                stmt.execute();
                Parameter.bindObject(stmt, 1, 1.234);
                Parameter.bindObject(stmt, 2, new java.sql.Timestamp(System.currentTimeMillis()));
                stmt.execute();
                Parameter.bindObject(stmt, 1, "string");
                stmt.execute();
                Parameter.bindObject(stmt, 1, ContainerManager.getRoot());
                Parameter.bindObject(stmt, 2, new java.util.Date());
                stmt.execute();
                Parameter.bindObject(stmt, 1, MyEnum.BETTY);
                stmt.execute();

                new Parameter("true", true, dialect).bind(stmt,1);
                stmt.execute();
                new Parameter("false", false, dialect).bind(stmt,1);
                stmt.execute();
            }
            finally
            {
                PreparedStatement cleanup = conn.prepareStatement("DROP TABLE " + name);
                cleanup.execute();
                conn.close();
            }
        }


        private Map<String, String> _quickMap(String q)
        {
            Map<String, String> m = new HashMap<String, String>();
            Pair[] pairs = PageFlowUtil.fromQueryString(q);
            for (Pair p : pairs)
                m.put((String)p.first, (String)p.second);
            return m;
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }


    static public LinkedHashMap<FieldKey,ColumnInfo> createFieldKeyMap(TableInfo table)
    {
        LinkedHashMap<FieldKey,ColumnInfo> ret = new LinkedHashMap<FieldKey,ColumnInfo>();
        for (ColumnInfo column : table.getColumns())
        {
            ret.put(column.getFieldKey(), column);
        }
        return ret;
    }
    

    static public Map<String, ColumnInfo> createColumnMap(TableInfo table, Collection<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<ColumnInfo> ret = new CaseInsensitiveHashMap<ColumnInfo>();
        if (columns != null)
        {
            for (ColumnInfo column : columns)
            {
                ret.put(column.getName(), column);
            }
        }
        if (table != null)
        {
            for (String name : table.getColumnNameSet())
            {
                if (ret.containsKey(name))
                    continue;
                ColumnInfo column = table.getColumn(name);
                if (column != null)
                {
                    ret.put(name, column);
                }
            }
        }
        return ret;
    }
}
