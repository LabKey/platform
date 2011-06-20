/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Join;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.etl.AbstractDataIterator;
import org.labkey.api.etl.Pump;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;

import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.CachedRowSet;
import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.stripEnd;

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
        return _executeQuery(conn, sql, parameters, false, null, null);
    }

    private static ResultSet _executeQuery(Connection conn, String sql, Object[] parameters, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Integer statementRowCount)
            throws SQLException
    {
        ResultSet rs;

        if (null == parameters || 0 == parameters.length)
        {
            Statement statement = conn.createStatement(scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            if (null != statementRowCount)
            {
                statement.setMaxRows(statementRowCount == NO_ROWS ? 0 : statementRowCount);
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
                stmt.setMaxRows(statementRowCount);
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


    public static int execute(Connection conn, String sql, @NotNull Object... parameters)
            throws SQLException
    {
        Statement stmt = null;

        try
        {
            if (0 == parameters.length)
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

        int i = 1;

        for (Object value : parameters)
        {
            // UNDONE: this code belongs in Parameter._bind()
            //Parameter validation
            //Bug 1996 - rossb:  Generally, we let JDBC validate the
            //parameters and throw exceptions, however, it doesn't recognize NaN
            //properly which can lead to database corruption.  Trap that here
            {
                //if the input parameter is NaN, throw a sql exception
                boolean isInvalid = false;
                if (value instanceof Float)
                {
                    isInvalid = value.equals(Float.NaN);
                }
                else if (value instanceof Double)
                {
                    isInvalid = value.equals(Double.NaN);
                }

                if (isInvalid)
                {
                    throw new SQLException("Illegal argument (" + Integer.toString(i) + ") to SQL Statement:  " + value.toString() + " is not a valid parameter");
                }
            }

            Parameter p = new Parameter(stmt, i);
            p.setValue(value);
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

    private static ResultSet executeQuery(DbSchema schema, String sql, Object[] parameters, int rowCount, long scrollOffset, boolean cache, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Logger log, @Nullable Integer statementRowCount)
            throws SQLException
    {
        if (log == null) log = _log;
        Connection conn = null;
        ResultSet rs = null;
        boolean queryFailed = false;

        try
        {
            conn = schema.getScope().getConnection(log);
            rs = _executeQuery(conn, sql, parameters, scrollable, asyncRequest, statementRowCount);

            while (scrollOffset > 0 && rs.next())
                scrollOffset--;

            if (cache)
                return cacheResultSet(rs, rowCount, asyncRequest);
            else
                return new ResultSetImpl(conn, schema, rs, rowCount);
        }
        catch(SQLException e)
        {
            // For every SQLException log error, query SQL, and params, then throw
            _log.error("internalExecuteQuery", e);
            _logQuery(Level.ERROR, sql, parameters, conn);
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
                CachedRowSetImpl copy = (CachedRowSetImpl)cacheResultSet(rs, 0, null);
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


    public static int execute(DbSchema schema, String sql, @NotNull Object... parameters)
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
    public static void batchExecute(DbSchema schema, String sql, Iterable<? extends Collection<?>> paramList)
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

    // return a result from a one row one column resultset or null.
    // K should be a string or number type.
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

    // return a result from a one row one column resultset
    // does not distinguish between not found, and set NULL
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
        DOUBLE(Double.class) { Double getObject(ResultSet rs) throws SQLException { double d = rs.getDouble(1); return rs.wasNull() ? null : d ; }},
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
            _logQuery(Level.WARN, sql, parameters, conn);
        }
        else
        {
            _log.error("SQL Exception", e);
            _logQuery(Level.ERROR, sql, parameters, conn);
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

        if (null != conn) schema.getScope().releaseConnection(conn);
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
            ensureRequiredColumns(table, cols, filter, sort, null);
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


    protected static Map<String, Object> _getTableData(TableInfo table, Map<String, Object> fields, boolean insert)
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
        if (null != colVersion && colVersion != colModified && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
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
        if (null != colVersion && colVersion != colModified && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
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
                    // use BeanUtils instead of PropertyUtils because BeanUtils will use a registered Converter
                    BeanUtils.copyProperty(fields, propName, value);
                }
                // UNDONE: horrible postgres hack..., not general fix
                else if (propName.endsWith("id"))
                {
                    propName = propName.substring(0,propName.length()-2) + "Id";
                    if (PropertyUtils.isWriteable(fields, propName))
                        BeanUtils.copyProperty(fields, propName, value);
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


    public static <K> K update(User user, TableInfo table, K fieldsIn, Object pkVals) throws SQLException
    {
        return update(user, table, fieldsIn, pkVals, null);
    }


    public static <K> K update(User user, TableInfo table, K fieldsIn, Object pkVals, @Nullable Filter filter) throws SQLException
    {
        assert (table.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB): (table.getName() + " is not in the physical database.");
        assert null != pkVals;

        // _executeTriggers(table, previous, fields);

        StringBuilder setSQL = new StringBuilder();
        StringBuilder whereSQL = new StringBuilder();
        ArrayList<Object> parametersSet = new ArrayList<Object>();
        ArrayList<Object> parametersWhere = new ArrayList<Object>();
        String comma = "";

        // UNDONE -- rowVersion
        List<ColumnInfo> columnPK = table.getPkColumns();
        Object[] pkValsArray;

        assert null != columnPK;
        assert columnPK.size() == 1 || ((Object[]) pkVals).length == columnPK.size();

        if (columnPK.size() == 1 && !pkVals.getClass().isArray())
            pkValsArray = new Object[]{pkVals};
        else
            pkValsArray = (Object[]) pkVals;

        String whereAND = "WHERE ";

        if (null != filter)
        {
            SQLFragment fragment = filter.getSQLFragment(table, null);
            whereSQL.append(fragment.getSQL());
            parametersWhere.addAll(fragment.getParams());
            whereAND = " AND ";
        }

        for (int i = 0; i < columnPK.size(); i++)
        {
            whereSQL.append(whereAND);
            whereSQL.append(columnPK.get(i).getSelectName());
            whereSQL.append("=?");
            parametersWhere.add(pkValsArray[i]);
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
        SQLFragment updateSQL = new SQLFragment("UPDATE " + table.getSelectName() + "\n\t" +
                "SET " + setSQL + "\n\t" +
                whereSQL);

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;

        updateSQL.addAll(parametersSet);
        updateSQL.addAll(parametersWhere);

        try
        {
            conn = schema.getScope().getConnection();
            int count = execute(table.getSchema(), updateSQL);

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
            _doCatch(updateSQL.getSQL(), updateSQL.getParamsArray(), conn, e);
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
        return selectObject(table, null, pk, clss);
    }


    public static <K> K selectObject(TableInfo table, @Nullable Container c, Object pk, Class<K> clss)
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

        if (null != c)
            filter.addCondition("container", c);

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
        return QueryService.get().getSelectSQL(table, allColumns, filter, sort, 0, 0, false);
    }


    public static SQLFragment getSelectSQL(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort)
    {
        return QueryService.get().getSelectSQL(table, columns, filter, sort, 0, 0, false);
    }


    public static ResultSet select(TableInfo table, Set<String> select, Filter filter, Sort sort)
            throws SQLException
    {
        return select(table, columnInfosList(table, select), filter, sort);
    }


    public static Results select(TableInfo table, Collection<ColumnInfo> columns, Filter filter, Sort sort)
            throws SQLException
    {
        return QueryService.get().select(table, columns, filter, sort);
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

        // TODO: Use decideRowCount() here?
        SQLFragment sql = QueryService.get().getSelectSQL(table, columns, filter, sort, queryRowCount, queryOffset, true);
        return internalExecuteQueryArray(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), clss, scrollOffset);
    }


    public static Results selectForDisplay(TableInfo table, Set<String> select, Map<String,Object> parameters, Filter filter, Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, columnInfosList(table, select), parameters, filter, sort, rowCount, offset);
    }


    public static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String,Object> parameters, Filter filter, Sort sort, int rowCount, long offset)
            throws SQLException
    {
        return selectForDisplay(table, select, parameters, filter, sort, rowCount, offset, true, false);
    }

    public static Map<String, Aggregate.Result>selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates,
            Collection<ColumnInfo> select, Map<String,Object> parameters, Filter filter, boolean cache) throws SQLException
    {
        return selectAggregatesForDisplay(table, aggregates, select, parameters, filter, cache, null);
    }

    private static Map<String, Aggregate.Result> selectAggregatesForDisplay(TableInfo table, List<Aggregate> aggregates,
            Collection<ColumnInfo> select, Map<String,Object> parameters, Filter filter, boolean cache, @Nullable AsyncQueryRequest asyncRequest)
            throws SQLException
    {
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        ensureRequiredColumns(table, columns, filter, null, aggregates);
        SQLFragment innerSql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, null, 0, 0, false);
        QueryService.get().bindNamedParameters(innerSql, parameters);
        QueryService.get().validateNamedParameters(innerSql);

        Map<String, ColumnInfo> columnMap = Table.createColumnMap(table, columns.values());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        boolean first = true;

        for (Aggregate agg : aggregates)
        {
            if (agg.isCountStar() || columnMap.containsKey(agg.getColumnName()))
            {
                if (first)
                    first = false;
                else
                    sql.append(", ");
                sql.append(agg.getSQL(table.getSqlDialect(), columnMap));
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
            rs = (Table.TableResultSet) executeQuery(table.getSchema(), sql.toString(), innerSql.getParams().toArray(), ALL_ROWS, 0, cache, false, asyncRequest, null, null);
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

    public static Map<String, Aggregate.Result> selectAggregatesForDisplayAsync(final TableInfo table, final List<Aggregate> aggregates,
            final Collection<ColumnInfo> select, final Map<String,Object> parameters, final Filter filter, final boolean cache, HttpServletResponse response)
            throws SQLException, IOException
    {
        final AsyncQueryRequest<Map<String, Aggregate.Result>> asyncRequest = new AsyncQueryRequest<Map<String, Aggregate.Result>>(response);
        return asyncRequest.waitForResult(new Callable<Map<String, Aggregate.Result>>() {
            public Map<String, Aggregate.Result> call() throws Exception
            {
                return selectAggregatesForDisplay(table, aggregates, select, parameters, filter, cache, asyncRequest);
            }
        });
    }


    public static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String,Object> parameters, Filter filter, Sort sort, int rowCount, long offset, boolean cache, boolean scrollable)
            throws SQLException
    {
        return selectForDisplay(table, select, parameters, filter, sort, rowCount, offset, cache, scrollable, null, null);
    }


    private static Results selectForDisplay(TableInfo table, Collection<ColumnInfo> select, Map<String,Object> parameters, Filter filter, Sort sort, int rowCount, long offset, boolean cache, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Logger log)
            throws SQLException
    {
        assert Table.checkAllColumns(table, select, "selectForDisplay() select columns");
        Map<String, ColumnInfo> columns = getDisplayColumnsList(select);
        assert Table.checkAllColumns(table, columns.values(), "selectForDisplay() results of getDisplayColumnsList()");
        ensureRequiredColumns(table, columns, filter, sort, null);
        assert Table.checkAllColumns(table, columns.values(), "selectForDisplay() after ensureRequiredColumns");

        long queryOffset = offset, scrollOffset = 0;
        int queryRowCount = rowCount;
        if (offset > 0 && !table.getSqlDialect().supportsOffset())
        {
            queryOffset = 0;
            scrollOffset = offset;
            queryRowCount = rowCount + (int)offset;
        }

        int decideRowCount = decideRowCount(queryRowCount, null);
        SQLFragment sql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, decideRowCount, queryOffset, true);
        QueryService.get().bindNamedParameters(sql, parameters);
        QueryService.get().validateNamedParameters(sql);
        Integer statementRowCount = (table.getSqlDialect().requiresStatementMaxRows() ? decideRowCount : null);  // TODO: clean this all up
        Table.TableResultSet rs = (Table.TableResultSet)executeQuery(table.getSchema(), sql.getSQL(), sql.getParams().toArray(), rowCount, scrollOffset, cache, scrollable, asyncRequest, log, statementRowCount);

        return new ResultsImpl(rs, columns.values());
    }


    public static Results selectForDisplayAsync(final TableInfo table, final Collection<ColumnInfo> select, Map<String,Object> parameters, final Filter filter, final Sort sort, final int rowCount, final long offset, final boolean cache, final boolean scrollable, HttpServletResponse response) throws SQLException, IOException
    {
        final Logger log = ConnectionWrapper.getConnectionLogger();
        final AsyncQueryRequest<Results> asyncRequest = new AsyncQueryRequest<Results>(response);
        final Map<String,Object> parametersCopy = new CaseInsensitiveHashMap<Object>();
        if (null != parameters)
            parametersCopy.putAll(parameters);
        return asyncRequest.waitForResult(new Callable<Results>()
		{
            public Results call() throws Exception
            {
                return selectForDisplay(table, select, parametersCopy, filter, sort, rowCount, offset, cache, scrollable, asyncRequest, log);
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
        ensureRequiredColumns(table, columns, filter, sort, null);
        SQLFragment sql = QueryService.get().getSelectSQL(table, new ArrayList<ColumnInfo>(columns.values()), filter, sort, 0, 0, true);
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


    public static void ensureRequiredColumns(TableInfo table, Map<String, ColumnInfo> cols, Filter filter, Sort sort, List<Aggregate> aggregates)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        Set<String> requiredColumns = new CaseInsensitiveHashSet();

        if (null != filter)
            requiredColumns.addAll(filter.getWhereParamNames());

        if (null != sort)
        {
            requiredColumns.addAll(sort.getRequiredColumnNames(cols));
        }

        if (null != aggregates)
        {
            for (Aggregate agg : aggregates)
                requiredColumns.add(agg.getColumnName());
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
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM (");
        sql.append(QueryService.get().getSelectSQL(tinfo, tinfo.getPkColumns(), null, null, Table.ALL_ROWS, 0, false));
        sql.append(") x");
        return Table.executeSingleton(tinfo.getSchema(), sql.getSQL(), sql.getParamsArray(), Long.class);
    }


    private static TableResultSet cacheResultSet(ResultSet rs, int rowCount, @Nullable AsyncQueryRequest asyncRequest) throws SQLException
    {
        CachedRowSetImpl crsi = new CachedRowSetImpl(rs, rowCount);

        if (null != asyncRequest && AppProps.getInstance().isDevMode())
            crsi.setStackTrace(asyncRequest.getCreationStackTrace());

        return crsi;
    }


    public interface TableResultSet extends ResultSet, Iterable<Map<String, Object>>
    {
        public boolean isComplete();

//        public boolean supportsGetRowMap();
        
        public Map<String, Object> getRowMap() throws SQLException;

        public Iterator<Map<String, Object>> iterator();

        String getTruncationMessage(int maxRows);

        /** @return the number of rows in the result set. -1 if unknown */
        int getSize();
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

        @Override
        public int getSize()
        {
            return -1;
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

//        @Override
//        public boolean supportsGetRowMap()
//        {
//            return false;
//        }

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


    private static void _logQuery(Level level, String sql, Object[] parameters, Connection conn)
    {
        if (!_log.isEnabledFor(level))
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
        _log.log(level, logEntry);
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


    public static class TestCase extends Assert
    {
        static private CoreSchema _core = CoreSchema.getInstance();

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


        @Test
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


        @Test
        public void testMaxRows() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            Results rsAll = Table.selectForDisplay(tinfo, Table.ALL_COLUMNS, null, null, null, 0, 0);
            rsAll.last();
            int rowCount = rsAll.getRow();
            assertTrue(((Table.TableResultSet)rsAll.getResultSet()).isComplete());
            rsAll.close();

            rowCount -= 2;
            Results rs = Table.selectForDisplay(tinfo, Table.ALL_COLUMNS, null, null, null, rowCount, 0);
            rs.last();
            int row = rs.getRow();
            assertTrue(row == rowCount);
            assertFalse(((Table.TableResultSet)rs.getResultSet()).isComplete());
            rs.close();
        }


        @Test
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


        @Test
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

        @Test
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
                        "(s VARCHAR(36), d " + dialect.sqlTypeNameFromSqlType(JdbcType.TIMESTAMP.sqlType) + ")");
                stmt.execute();
                stmt.close();

                String sql = "INSERT INTO " + name + " VALUES (?, ?)";
                stmt = conn.prepareStatement(sql);
                Parameter s = new Parameter(stmt, 1);
                Parameter d = new Parameter(stmt, 2, JdbcType.TIMESTAMP);

                s.setValue(4);
                d.setValue(GregorianCalendar.getInstance());
                stmt.execute();
                s.setValue(1.234);
                d.setValue(new java.sql.Timestamp(System.currentTimeMillis()));
                stmt.execute();
                s.setValue("string");
                d.setValue(null);
                stmt.execute();
                s.setValue(ContainerManager.getRoot());
                d.setValue(new java.util.Date());
                stmt.execute();
                s.setValue(MyEnum.BETTY);
                d.setValue(null);
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


    //
    // INSERT, UPDATE, DELETE helpers
    //
    // TODO convert other table methods to use these helpers
    //

    // SQLFragment version for convenience
    private static void appendSelectAutoIncrement(SqlDialect d, SQLFragment sqlf, TableInfo tinfo, String columnName)
    {
        // TODO why does appendSelectAutoIncrement prepend a semi-colon?
        String t = d.appendSelectAutoIncrement("", tinfo, columnName);
        t = StringUtils.strip(t, ";\n\r");
        sqlf.append(t);
    }



    private static void appendParameterOrVariable(SQLFragment f, SqlDialect d, boolean useVariable, Parameter p, Map<Parameter,String> names)
    {
        if (!useVariable)
        {
            f.append("?");
            f.add(p);
        }
        else
        {
            String v = names.get(p);
            if (null == v)
            {
                v =  (d.isSqlServer() ? "@p" : "_$p") + (names.size()+1);
                names.put(p,v);
            }
            f.append(v);
        }
    }


    /**
     * Create a reusable SQL Statement for inserting rows into an labkey relationship.  The relationship
     * persisted directly in the database (SchemaTableInfo), or via the OnotologyManager tables.
     *
     * QueryService shouldn't really know about the internals of exp.Object and exp.ObjectProperty etc.
     * However, I can only keep so many levels of abstraction in my head at once.
     *
     * NOTE: this is currently fairly expensive for updating one row into an Ontology stored relationship on Postgres.
     * This shouldn't be a big problem since we don't usually need to optimize the one row case, and we're moving
     * to provisioned tables for major datatypes.
     */
    public static Parameter.ParameterMap insertStatement(Connection conn, TableInfo tableInsert, Container c, User user, boolean selectIds, boolean autoFillDefaultColumns) throws SQLException
    {
        if (!(tableInsert instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        UpdateableTableInfo updatable = (UpdateableTableInfo)tableInsert;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = tableInsert.getSqlDialect();
        boolean useVariables = false;

        // helper for generating procedure/function variation
        Map<Parameter,String> parameterToVariable = new IdentityHashMap<Parameter,String>();
        
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        Parameter containerParameter = null;
        Parameter objecturiParameter = null;

        String comma = "";
        Set<String> done = Sets.newCaseInsensitiveHashSet();

        String objectIdVar = null;
        String setKeyword = d.isPostgreSQL() ? "" : "SET ";

        //
        // exp.Objects INSERT
        //

        SQLFragment sqlfDeclare = new SQLFragment();
        SQLFragment sqlfObject = new SQLFragment();
        SQLFragment sqlfObjectProperty = new SQLFragment();

        Domain domain = tableInsert.getDomain();
        DomainKind domainKind = tableInsert.getDomainKind();
        DomainProperty[] properties = null;
        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            properties = domain.getProperties();
            if (properties.length == 0)
                properties = null;
            if (null != properties)
            {
                if (!d.isPostgreSQL() && !d.isSqlServer())
                    throw new IllegalArgumentException("Domains are only supported for sql server and postgres");

                objectIdVar = d.isPostgreSQL() ? "_$objectid$_" : "@_objectid_";

                useVariables = d.isPostgreSQL();
                sqlfDeclare.append("DECLARE " + objectIdVar + " INT;\n");
                containerParameter = new Parameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);
                String parameterName = updatable.getObjectUriType() == UpdateableTableInfo.ObjectUriType.schemaColumn
                        ? updatable.getObjectURIColumnName()
                        : "objecturi";
                objecturiParameter = new Parameter(parameterName,JdbcType.VARCHAR);
                sqlfObject.append("INSERT INTO exp.Object (container, objecturi) ");
                sqlfObject.append("VALUES(");
                appendParameterOrVariable(sqlfObject, d, useVariables, containerParameter, parameterToVariable);
                sqlfObject.append(",");
                appendParameterOrVariable(sqlfObject, d, useVariables, objecturiParameter, parameterToVariable);
                sqlfObject.append(");\n");
                sqlfObject.append(setKeyword + objectIdVar + " = (");
                appendSelectAutoIncrement(d, sqlfObject, DbSchema.get("exp").getTable("object"), "objectid");
                sqlfObject.append(");\n");
            }
        }

        //
        // BASE TABLE INSERT()
        //

        SQLFragment cols = new SQLFragment();
        SQLFragment values = new SQLFragment();
        ColumnInfo col;

        col = table.getColumn("Container");
        if (null != col && null != user)
        {
            cols.append(comma).append("Container");
            if (null == containerParameter)
            {
                containerParameter = new Parameter("container", JdbcType.VARCHAR);
//                if (autoFillDefaultColumns && null != c)
//                    containerParameter.setValue(c.getId(), true);
            }
            appendParameterOrVariable(values, d, useVariables, containerParameter, parameterToVariable);
            done.add("Container");
            comma = ",";
        }
        col = table.getColumn("Owner");
        if (autoFillDefaultColumns && null != col && null != user)
        {
            cols.append(comma).append("Owner");
            values.append(comma).append(user.getUserId());
            done.add("Owner");
            comma = ",";
        }
        col = table.getColumn("CreatedBy");
        if (autoFillDefaultColumns && null != col && null != user)
        {
            cols.append(comma).append("CreatedBy");
            values.append(comma).append(user.getUserId());
            done.add("CreatedBy");
            comma = ",";
        }
        col = table.getColumn("Created");
        if (autoFillDefaultColumns && null != col)
        {
            cols.append(comma).append("Created");
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add("Created");
            comma = ",";
        }
        ColumnInfo colModifiedBy = table.getColumn("Modified");
        if (autoFillDefaultColumns && null != colModifiedBy && null != user)
        {
            cols.append(comma).append("ModifiedBy");
            values.append(comma).append(user.getUserId());
            done.add("ModifiedBy");
            comma = ",";
        }
        ColumnInfo colModified = table.getColumn("Modified");
        if (autoFillDefaultColumns && null != colModified)
        {
            cols.append(comma).append("Modified");
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add("Modified");
            comma = ",";
        }
        ColumnInfo colVersion = table.getVersionColumn();
        if (autoFillDefaultColumns && null != colVersion && !done.contains(colVersion.getName()) && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
        {
            cols.append(comma).append(colVersion.getSelectName());
            values.append(comma).append("CAST('" + ts + "' AS " + d.getDefaultDateTimeDataType() + ")");
            done.add(colVersion.getName());
            comma = ",";
        }

        String objectIdColumnName = StringUtils.trimToNull(updatable.getObjectIdColumnName());
        ColumnInfo autoIncrementColumn = null;

        for (ColumnInfo column : table.getColumns())
        {
            if (column.isAutoIncrement())
            {
                autoIncrementColumn = column;
                continue;
            }
            if (column.isVersionColumn())
                continue;
            if (done.contains(column.getName()))
                continue;
            done.add(column.getName());

            cols.append(comma).append(column.getSelectName());
            if (column.getName().equalsIgnoreCase(objectIdColumnName))
            {
                values.append(comma).append(objectIdVar);
            }
            else if (column.getName().equalsIgnoreCase(updatable.getObjectURIColumnName()) && null != objecturiParameter)
            {
                values.append(comma);
                appendParameterOrVariable(values, d, useVariables, objecturiParameter, parameterToVariable);
            }
            else
            {
                Parameter p = new Parameter(column, null);
                values.append(comma);
                appendParameterOrVariable(values, d, useVariables, p, parameterToVariable);
            }
            comma = ", ";
        }

        SQLFragment sqlfSelectIds = null;
        Integer selectRowIdIndex = null;
        Integer selectObjectIdIndex = null;
        int countReturnIds = 0;
        if (selectIds && (null != autoIncrementColumn || null != objectIdVar))
        {
            sqlfSelectIds = new SQLFragment("");
            String prefix = "SELECT ";
            int index = 1;
            if (null != autoIncrementColumn)
            {
                appendSelectAutoIncrement(d, sqlfSelectIds, table, autoIncrementColumn.getName());
                selectRowIdIndex = ++countReturnIds;
                prefix = ", ";
            }
            if (null != objectIdVar)
            {
                sqlfSelectIds.append(prefix);
                sqlfSelectIds.append(objectIdVar);
                selectObjectIdIndex = ++countReturnIds;
            }
            sqlfSelectIds.append(";\n");
        }

        SQLFragment sqlfInsertInto = new SQLFragment();
        sqlfInsertInto.append("INSERT INTO " + table + " (");
        sqlfInsertInto.append(cols).append(")\nVALUES (").append(values).append(");\n");

        //
        // ObjectProperty
        //

        if (null != properties)
        {
            Set<String> skip = ((UpdateableTableInfo)tableInsert).skipProperties();
            if (null != skip)
                done.addAll(skip);

            for (DomainProperty dp : domain.getProperties())
            {
                // ignore property that 'wraps' a hard column
                if (done.contains(dp.getName()))
                    continue;
                // CONSIDER: IF (p IS NOT NULL) THEN ...
                sqlfObjectProperty.append("INSERT INTO exp.ObjectProperty (objectid, propertyid, typetag, mvindicator, ");
                PropertyType propertyType = dp.getPropertyDescriptor().getPropertyType();
                switch (propertyType.getStorageType())
                {
                    case 's':
                        sqlfObjectProperty.append("stringValue");
                        break;
                    case 'd':
                        sqlfObjectProperty.append("dateTimeValue");
                        break;
                    case 'f':
                        sqlfObjectProperty.append("floatValue");
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown property type: " + propertyType);
                }
                sqlfObjectProperty.append(") VALUES (");
                sqlfObjectProperty.append(objectIdVar);
                sqlfObjectProperty.append(",").append(dp.getPropertyId());
                sqlfObjectProperty.append(",'").append(propertyType.getStorageType()).append("'");
                Parameter mv = new Parameter(dp.getName()+ MvColumn.MV_INDICATOR_SUFFIX, dp.getPropertyURI() + MvColumn.MV_INDICATOR_SUFFIX, null, JdbcType.VARCHAR);
                sqlfObjectProperty.append(",");
                appendParameterOrVariable(sqlfObjectProperty, d,useVariables, mv, parameterToVariable);
                Parameter v = new Parameter(dp.getName(), dp.getPropertyURI(), null, propertyType.getJdbcType());
                sqlfObjectProperty.append(",");
                appendParameterOrVariable(sqlfObjectProperty, d,useVariables, v, parameterToVariable);
                sqlfObjectProperty.append(");\n");
            }
        }

        //
        // PREPARE
        //

        Parameter.ParameterMap ret;

        if (!useVariables)
        {
            SQLFragment script = new SQLFragment();
            script.append(sqlfDeclare);
            script.append(sqlfObject);
            script.append(sqlfInsertInto);
            script.append(sqlfObjectProperty);
            if (null != sqlfSelectIds)
                script.append(sqlfSelectIds);
            ret = new Parameter.ParameterMap(conn, script, updatable.remapSchemaColumns());
        }
        else
        {
            // wrap in a function
            SQLFragment fn = new SQLFragment();
            String fnName = d.getGlobalTempTablePrefix() + "fn_" + GUID.makeHash();
            fn.append("CREATE FUNCTION " + fnName + "(");
            // TODO d.execute() doesn't handle temp schema
            SQLFragment call = new SQLFragment("SELECT ");
            if (countReturnIds > 0)
                call.append("* FROM ");
            call.append(fnName + "(");
            final SQLFragment drop = new SQLFragment("DROP FUNCTION " + fnName + "(");
            comma = "";
            for (Map.Entry<Parameter,String> e : parameterToVariable.entrySet())
            {
                Parameter p = e.getKey();
                String variable = e.getValue();
                String type = d.sqlTypeNameFromSqlType(p.getType().sqlType);
                fn.append("\n").append(comma);
                fn.append(variable);
                fn.append(" ");
                fn.append(type);
                fn.append(" -- " + p.getName());
                drop.append(comma).append(type);
                call.append(comma).append("?");
                call.add(p);
                comma = ",";
            }
            fn.append("\n) RETURNS ");
            if (countReturnIds>0)
                fn.append("SETOF RECORD");
            else
                fn.append("void");
            fn.append(" AS $$\n");
            drop.append(");");
            call.append(")");
            if (countReturnIds > 0)
            {
                if (countReturnIds == 1)
                    call.append(" AS x(A int)");
                else
                    call.append(" AS x(A int, B int)");
            }
            call.append(";");
            fn.append(sqlfDeclare);
            fn.append("BEGIN\n");
            fn.append(sqlfObject);
            fn.append(sqlfInsertInto);
            fn.append(sqlfObjectProperty);
            if (null != sqlfSelectIds)
            {
                fn.append("\nRETURN QUERY ");
                fn.append(sqlfSelectIds);
            }
            fn.append("\nEND;\n$$ LANGUAGE plpgsql;\n");

            Table.execute(table.getSchema(), fn);
            ret = new Parameter.ParameterMap(conn, call, updatable.remapSchemaColumns());
            ret.onClose(new Runnable() { @Override public void run()
            {
                try
                {
                    Table.execute(ExperimentService.get().getSchema(),drop);
                }
                catch (SQLException x)
                {
                    Logger.getLogger(Table.class).error("Error dropping temp function", x);
                }
            }});
        }

//        if (null != constants)
//        {
//            for (Map.Entry e : constants.entrySet())
//            {
//                Parameter p = ret._map.get(e.getKey());
//                if (null != p)
//                    p.setValue(e.getValue(), true);
//            }
//        }

        ret.setRowIdIndex(selectRowIdIndex);
        ret.setObjectIdIndex(selectObjectIdIndex);

        return ret;
    }


    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix)
    {
        int bad = 0;

        for (ColumnInfo column : columns)
            if (!checkColumn(table, column, prefix))
                bad++;

        // Check all the columns in the TableInfo to determine if the TableInfo is corrupt
        for (ColumnInfo column : table.getColumns())
            if (!checkColumn(table, column, "TableInfo.getColumns() for " + prefix))
                bad++;

        // Check the pk columns in the TableInfo
        for (ColumnInfo column : table.getPkColumns())
            if (!checkColumn(table, column, "TableInfo.getPkColumns() for " + prefix))
                bad++;

        return 0 == bad;
    }


    public static boolean checkColumn(TableInfo table, ColumnInfo column, String prefix)
    {
        Logger log = Logger.getLogger(Table.class);

        if (column.getParentTable() != table)
        {
            log.warn(prefix + ": Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table);
            return false;
        }
        else
        {
            return true;
        }
    }


    public static Parameter.ParameterMap deleteStatement(Connection conn, TableInfo tableDelete /*, Set<String> columns */) throws SQLException
    {
        if (!(tableDelete instanceof UpdateableTableInfo))
            throw new IllegalArgumentException();

        if (null == conn)
            conn = tableDelete.getSchema().getScope().getConnection();

        UpdateableTableInfo updatable = (UpdateableTableInfo)tableDelete;
        TableInfo table = updatable.getSchemaTableInfo();

        if (!(table instanceof SchemaTableInfo))
            throw new IllegalArgumentException();
        if (null == ((SchemaTableInfo)table).getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = tableDelete.getSqlDialect();

        List<ColumnInfo> columnPK = table.getPkColumns();
        List<Parameter> paramPK = new ArrayList<Parameter>(columnPK.size());
        for (ColumnInfo pk : columnPK)
            paramPK.add(new Parameter(pk.getName(), null, pk.getJdbcType()));
        Parameter paramContainer = new Parameter("container", null, JdbcType.VARCHAR);

        SQLFragment sqlfWhere = new SQLFragment();
        sqlfWhere.append("\nWHERE " );
        String and = "";
        for (int i=0 ; i<columnPK.size() ; i++)
        {
            ColumnInfo pk = columnPK.get(i);
            Parameter p = paramPK.get(i);
            sqlfWhere.append(and); and = " AND ";
            sqlfWhere.append(pk.getSelectName()).append("=?");
            sqlfWhere.add(p);
        }
        if (null != table.getColumn("container"))
        {
            sqlfWhere.append(and);
            sqlfWhere.append("container=?");
            sqlfWhere.add(paramContainer);
        }

        SQLFragment sqlfDelete = new SQLFragment();
        SQLFragment sqlfDeleteObject = null;
        SQLFragment sqlfDeleteTable;

        //
        // exp.Objects delete
        //

        Domain domain = tableDelete.getDomain();
        DomainKind domainKind = tableDelete.getDomainKind();
        if (null != domain && null != domainKind && StringUtils.isEmpty(domainKind.getStorageSchemaName()))
        {
            if (!d.isPostgreSQL() && !d.isSqlServer())
                throw new IllegalArgumentException("Domains are only supported for sql server and postgres");

            String objectIdColumnName = updatable.getObjectIdColumnName();
            String objectURIColumnName = updatable.getObjectURIColumnName();

            if (null == objectIdColumnName && null == objectURIColumnName)
                throw new IllegalStateException("Join column for exp.Object must be defined");

            SQLFragment sqlfSelectKey = new SQLFragment();
            if (null != objectIdColumnName)
            {
                String keyName = StringUtils.defaultString(objectIdColumnName, objectURIColumnName);
                ColumnInfo keyCol = table.getColumn(keyName);
                sqlfSelectKey.append("SELECT ").append(keyCol.getSelectName());
                sqlfSelectKey.append("FROM ").append(table.getFromSQL("X"));
                sqlfSelectKey.append(sqlfWhere);
            }

            String fn = null==objectIdColumnName ? "deleteObject" : "deleteObjectByid";
            SQLFragment deleteArguments = new SQLFragment("?, ");
            deleteArguments.add(paramContainer);
            deleteArguments.append(sqlfSelectKey);
            sqlfDeleteObject = d.execute(ExperimentService.get().getSchema(), fn, deleteArguments);
        }

        //
        // BASE TABLE delete
        //

        sqlfDeleteTable = new SQLFragment("DELETE " + table.getSelectName());
        sqlfDelete.append(sqlfWhere);

        if (null != sqlfDeleteObject)
        {
            sqlfDelete.append(sqlfDeleteObject);
            sqlfDelete.append(";\n");
        }
        sqlfDelete.append(sqlfDeleteTable);

        return new Parameter.ParameterMap(conn, sqlfDelete, updatable.remapSchemaColumns());
    }


    public static class TestDataIterator extends AbstractDataIterator
    {
        String guid = GUID.makeGUID();
        Date date = new Date();

        Object[][] _data = new Object[][]
        {
            new Object[] {1, "One", 101, true, date, guid},
            new Object[] {2, "Two", 102, true, date, guid},
            new Object[] {3, "Three", 103, true, date, guid}
        };

        static class _ColumnInfo extends ColumnInfo
        {
            _ColumnInfo(String name, JdbcType type)
            {
                super(name, type);
                setReadOnly(true);
            }
        }

        static ColumnInfo[] _cols = new ColumnInfo[]
        {
            new _ColumnInfo("_row", JdbcType.INTEGER),
            new _ColumnInfo("Text", JdbcType.VARCHAR),
            new _ColumnInfo("IntNotNull", JdbcType.INTEGER),
            new _ColumnInfo("BitNotNull", JdbcType.BOOLEAN),
            new _ColumnInfo("DateTimeNotNull", JdbcType.TIMESTAMP),
            new _ColumnInfo("EntityId", JdbcType.VARCHAR)
        };

        int currentRow = -1;

        TestDataIterator()
        {
            super(null);
        }

        @Override
        public int getColumnCount()
        {
            return _cols.length-1;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return _cols[i];
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            return ++currentRow < _data.length;
        }

        @Override
        public Object get(int i)
        {
            return _data[currentRow][i];
        }

        @Override
        public void close() throws IOException
        {
        }
    }


    public static class DataIteratorTestCase extends Assert
    {
        @Test
        public void test() throws Exception
        {
            TableInfo testTable = DbSchema.get("test").getTable("TestTable");

            BatchValidationException errors = new BatchValidationException();
            TestDataIterator extract = new TestDataIterator();
            SimpleTranslator translate = new SimpleTranslator(extract, errors);
            translate.selectAll();
            translate.addBuiltInColumns(JunitUtil.getTestContainer(), TestContext.get().getUser(), testTable, false);

            TableInsertDataIterator load = TableInsertDataIterator.create(
                    translate,
                    testTable,
                    errors
            );
            new Pump(load, errors).run();

            assertFalse(errors.hasErrors());
            
            Table.execute(testTable.getSchema(), "delete from test.testtable where entityid = '" + extract.guid + "'");
        }
    }
}
