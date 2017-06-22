/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.dataiterator.AbstractDataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Table manipulation methods
 * <p/>
 * select/insert/update/delete
 */

public class Table
{
    public static final String SQLSTATE_TRANSACTION_STATE = "25000";
    public static final int ERROR_ROWVERSION = 10001;
    public static final int ERROR_DELETED = 10002;
    public static final int ERROR_TABLEDELETED = 10003;

    private static final Logger _log = Logger.getLogger(Table.class);

    // Return all rows instead of limiting to the top n
    public static final int ALL_ROWS = -1;
    // Return no rows -- useful for query validation or when you need just metadata
    public static final int NO_ROWS = 0;

    // Makes long parameter lists easier to read
    public static final int NO_OFFSET = 0;

    public static final String ENTITY_ID_COLUMN_NAME = "EntityId";
    public static final String OWNER_COLUMN_NAME = "Owner";
    public static final String CREATED_BY_COLUMN_NAME = "CreatedBy";
    public static final String CREATED_COLUMN_NAME = "Created";
    public static final String MODIFIED_BY_COLUMN_NAME = "ModifiedBy";
    public static final String MODIFIED_COLUMN_NAME = "Modified";

    /** Columns that are magically populated as part of an insert or update operation */
    public static final Set<String> AUTOPOPULATED_COLUMN_NAMES = Collections.unmodifiableSet(new CaseInsensitiveHashSet(PageFlowUtil.set(
                    ENTITY_ID_COLUMN_NAME,
                    OWNER_COLUMN_NAME,
                    CREATED_BY_COLUMN_NAME,
                    CREATED_COLUMN_NAME,
                    MODIFIED_BY_COLUMN_NAME,
                    MODIFIED_COLUMN_NAME)));

    private Table()
    {
    }

    public static boolean validMaxRows(int maxRows)
    {
        return NO_ROWS == maxRows | ALL_ROWS == maxRows | maxRows > 0;
    }

    // ================== These methods have not been converted to Selector/Executor ==================

    // Careful: caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static PreparedStatement prepareStatement(Connection conn, String sql, Collection<?> parameters, List<Parameter> jdbcParameters) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(sql);
        setParameters(stmt, parameters, jdbcParameters);
        MemTracker.getInstance().put(stmt);
        return stmt;
    }


    public static void setParameters(PreparedStatement stmt, Collection<?> parameters, List<Parameter> jdbcParameters) throws SQLException
    {
        if (null == parameters)
            return;

        int i = 1;

        for (Object value : parameters)
        {
            //Parameter validation
            //Bug 1996 - rossb:  Generally, we let JDBC validate the
            //parameters and throw exceptions, however, it doesn't recognize NaN
            //properly which can lead to database corruption.
            // [This is now handled by making a special value (in ResultSetUtil) to hand to the DB]

            Parameter p = new Parameter(stmt, i);
            p.setValue(value);
            jdbcParameters.add(p);
            i++;
        }
    }

    /** @return if this is a statement that starts with SELECT and contains FROM, ignoring comment lines that start with "--" */
    public static boolean isSelect(String sql)
    {
        boolean select = false;

        for (String sqlLine : sql.split("\\r?\\n"))
        {
            sqlLine = sqlLine.trim();
            if (!sqlLine.startsWith("--"))
            {
                // First non-comment line must start with SELECT
                if (!select)
                {
                    if (StringUtils.startsWithIgnoreCase(sqlLine, "SELECT"))
                        select = true;
                    else
                        return false;
                }

                // We must also see a FROM clause so we don't flag stored procedure invocations, #22648
                if (StringUtils.containsIgnoreCase(sqlLine, "FROM"))
                    return true;
            }
        }
        return false;
    }


    // Careful: Caller must track and clean up parameters (e.g., close InputStreams) after execution is complete
    public static void batchExecute(DbSchema schema, String sql, Iterable<? extends Collection<?>> paramList) throws SQLException
    {
        Connection conn = schema.getScope().getConnection();
        PreparedStatement stmt = null;

        try (Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
        {
            stmt = conn.prepareStatement(sql);
            int paramCounter = 0;
            for (Collection<?> params : paramList)
            {
                setParameters(stmt, params, jdbcParameters);
                stmt.addBatch();

                paramCounter += params.size();
                if (paramCounter > 1000)
                {
                    paramCounter = 0;
                    stmt.executeBatch();
                    jdbcParameters.close();
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

            logException(new SQLFragment(sql), conn, e, Level.WARN);
            throw(e);
        }
        finally
        {
            doClose(null, stmt, conn, schema.getScope());
        }
    }


    static void batchExecute1String(DbSchema schema, String sql, Iterable<String> paramList) throws SQLException
    {
        Connection conn = schema.getScope().getConnection();
        PreparedStatement stmt = null;

        try
        {
            stmt = conn.prepareStatement(sql);
            int paramCounter = 0;
            for (String s : paramList)
            {
                stmt.setString(1, s);
                stmt.addBatch();
                paramCounter++;
                if (paramCounter > 2000)
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

            logException(new SQLFragment(sql), conn, e, Level.WARN);
            throw(e);
        }
        finally
        {
            doClose(null, stmt, conn, schema.getScope());
        }
    }

    public static void batchExecute1Integer(DbSchema schema, @NotNull String sql1, @Nullable String sql100, List<Integer> paramList) throws SQLException
    {
        Connection conn = schema.getScope().getConnection();
        PreparedStatement stmt100 = null;
        PreparedStatement stmt1 = null;

        try
        {
            int paramCounter = 0;

            // insert by 100's
            int outerIndex = 0;
            if (null != sql100 && paramList.size() >= 100)
            {
                stmt100 = conn.prepareStatement(sql100);
                for (; outerIndex < paramList.size() - 100; outerIndex += 100)
                {
                    for (int index = outerIndex; index < outerIndex + 100; index++)
                    {
                        Integer I = paramList.get(index);
                        if (null == I)
                            stmt100.setNull(index % 100 + 1, Types.INTEGER);
                        else
                            stmt100.setInt(index % 100 + 1, I.intValue());
                        paramCounter++;
                    }
                    stmt100.addBatch();
                    if (paramCounter > 10000)
                    {
                        stmt100.executeBatch();
                        paramCounter = 0;
                    }
                }
                if (paramCounter > 0)
                {
                    stmt100.executeBatch();
                    paramCounter = 0;
                }
            }

            // insert by 1's
            stmt1 = conn.prepareStatement(sql1);
            for (int index=outerIndex ; index<paramList.size() ; index++)
            {
                Integer I = paramList.get(index);
                if (null == I)
                    stmt1.setNull(1, Types.INTEGER);
                else
                    stmt1.setInt(1, I.intValue());
                stmt1.addBatch();
                paramCounter++;
                if (paramCounter > 1000)
                {
                    stmt1.executeBatch();
                    paramCounter = 0;
                }
            }
            if (paramCounter > 0)
                stmt1.executeBatch();
        }
        catch (SQLException e)
        {
            if (e instanceof BatchUpdateException)
            {
                if (null != e.getNextException())
                    e = e.getNextException();
            }

            logException(new SQLFragment(sql1), conn, e, Level.WARN);
            throw(e);
        }
        finally
        {
            doClose(null, stmt100, conn, schema.getScope());
            doClose(null, stmt1, conn, schema.getScope());
        }
    }


    private static Map<Class, Getter> _getterMap = new HashMap<>(10);

    enum Getter
    {
        STRING(String.class) {
            String getObject(ResultSet rs, int i) throws SQLException { return rs.getString(i); }
            String getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getString(columnLabel); }
        },
        INTEGER(Integer.class) {
            Integer getObject(ResultSet rs, int i) throws SQLException { int n = rs.getInt(i); return rs.wasNull() ? null : n ; }
            Integer getObject(ResultSet rs, String columnLabel) throws SQLException { int n = rs.getInt(columnLabel); return rs.wasNull() ? null : n ; }
        },
        DOUBLE(Double.class) {
            Double getObject(ResultSet rs, int i) throws SQLException { double d = rs.getDouble(i); return rs.wasNull() ? null : d ; }
            Double getObject(ResultSet rs, String columnLabel) throws SQLException { double d = rs.getDouble(columnLabel); return rs.wasNull() ? null : d ; }
        },
        BOOLEAN(Boolean.class) {
            Boolean getObject(ResultSet rs, int i) throws SQLException { boolean f = rs.getBoolean(i); return rs.wasNull() ? null : f ; }
            Boolean getObject(ResultSet rs, String columnLabel) throws SQLException { boolean f = rs.getBoolean(columnLabel); return rs.wasNull() ? null : f ; }
        },
        LONG(Long.class) {
            Long getObject(ResultSet rs, int i) throws SQLException { long l = rs.getLong(i); return rs.wasNull() ? null : l; }
            Long getObject(ResultSet rs, String columnLabel) throws SQLException { long l = rs.getLong(columnLabel); return rs.wasNull() ? null : l; }
        },
        UTIL_DATE(Date.class) {
            Date getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }
            Date getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getTimestamp(columnLabel); }
        },
        BYTES(byte[].class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getBytes(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getBytes(columnLabel); }
        },
        TIMESTAMP(Timestamp.class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getTimestamp(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getTimestamp(columnLabel); }
        },
        OBJECT(Object.class) {
            Object getObject(ResultSet rs, int i) throws SQLException { return rs.getObject(i); }
            Object getObject(ResultSet rs, String columnLabel) throws SQLException { return rs.getObject(columnLabel); }
        };

        abstract Object getObject(ResultSet rs, int i) throws SQLException;
        abstract Object getObject(ResultSet rs, String columnName) throws SQLException;

        Object getObject(ResultSet rs) throws SQLException
        {
            return getObject(rs, 1);
        }

        Getter(Class c)
        {
            _getterMap.put(c, this);
        }

        public static <K> Getter forClass(Class<K> c)
        {
            return _getterMap.get(c);
        }
    }


    // Standard SQLException catch block: log exception, query SQL, and params
    static void logException(@Nullable SQLFragment sql, @Nullable Connection conn, SQLException e, Level logLevel)
    {
        if (SqlDialect.isCancelException(e))
        {
            return;
        }

        if (sql == null)
        {
            _log.error("SQL Exception, no SQL query text available", e);
        }
        else
        {
            String trim = sql.getSQL().trim();

            // Treat a ConstraintException during INSERT/UPDATE as a WARNING. Treat all other SQLExceptions as an ERROR.
            if (RuntimeSQLException.isConstraintException(e) && (StringUtils.startsWithIgnoreCase(trim, "INSERT") || StringUtils.startsWithIgnoreCase(trim, "UPDATE")))
            {
                // Log this ConstraintException if log Level is WARN (the default) or lower. Skip logging for callers that request just ERRORs.
                if (Level.WARN.isGreaterOrEqual(logLevel))
                {
                    _log.warn("SQL Exception", e);
                    _logQuery(Level.WARN, sql, conn);
                }
            }
            else
            {
                // Log this SQLException if log level is ERROR or lower.
                if (Level.ERROR.isGreaterOrEqual(logLevel))
                {
                    _log.error("SQL Exception", e);
                    _logQuery(Level.ERROR, sql, conn);
                }
            }
        }
    }


    /** Typical finally block cleanup. Tolerant of null or already closed JDBC resources */
    static void doClose(@Nullable ResultSet rs, @Nullable Statement stmt, @Nullable Connection conn, @NotNull DbScope scope)
    {
        try
        {
            if (stmt == null && rs != null && !rs.isClosed())
                stmt = rs.getStatement();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }

        try
        {
            if (null != rs) rs.close();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }
        try
        {
            if (null != stmt) stmt.close();
        }
        catch (SQLException x)
        {
            _log.error("doFinally", x);
        }

        if (null != conn) scope.releaseConnection(conn);
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
            fields = new CaseInsensitiveHashMap<>(fields);

        // special rename case
        if (fields.containsKey("containerId"))
            fields.put("container", fields.get("containerId"));

        List<ColumnInfo> columns = table.getColumns();
        Map<String, Object> m = new CaseInsensitiveHashMap<>(columns.size() * 2);

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
        return StringUtils.stripEnd(s, "\t\r\n ");
    }


    protected static void _insertSpecialFields(User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo col = table.getColumn(OWNER_COLUMN_NAME);
        if (null != col && null != user)
            fields.put(OWNER_COLUMN_NAME, user.getUserId());
        col = table.getColumn(CREATED_BY_COLUMN_NAME);
        if (null != col && null != user)
            fields.put(CREATED_BY_COLUMN_NAME, user.getUserId());
        col = table.getColumn(CREATED_COLUMN_NAME);
        if (null != col)
        {
            Date dateCreated = (Date)fields.get(CREATED_COLUMN_NAME);
            if (null == dateCreated || 0 == dateCreated.getTime())
                fields.put(CREATED_COLUMN_NAME, date);
        }
        col = table.getColumn(ENTITY_ID_COLUMN_NAME);
        if (col != null && fields.get(ENTITY_ID_COLUMN_NAME) == null)
            fields.put(ENTITY_ID_COLUMN_NAME, GUID.makeGUID());
    }

    protected static void _copyInsertSpecialFields(Object returnObject, Map<String, Object> fields)
    {
        if (returnObject == fields)
            return;

        // make sure that any GUID generated in this routine is stored in the returned object
        if (fields.containsKey(ENTITY_ID_COLUMN_NAME))
            _setProperty(returnObject, ENTITY_ID_COLUMN_NAME, fields.get(ENTITY_ID_COLUMN_NAME));
        if (fields.containsKey(OWNER_COLUMN_NAME))
            _setProperty(returnObject, OWNER_COLUMN_NAME, fields.get(OWNER_COLUMN_NAME));
        if (fields.containsKey(CREATED_COLUMN_NAME))
            _setProperty(returnObject, CREATED_COLUMN_NAME, fields.get(CREATED_COLUMN_NAME));
        if (fields.containsKey(CREATED_BY_COLUMN_NAME))
            _setProperty(returnObject, CREATED_BY_COLUMN_NAME, fields.get(CREATED_BY_COLUMN_NAME));
    }

    protected static void _updateSpecialFields(@Nullable User user, TableInfo table, Map<String, Object> fields, java.sql.Timestamp date)
    {
        ColumnInfo colModifiedBy = table.getColumn(MODIFIED_BY_COLUMN_NAME);
        if (null != colModifiedBy && null != user)
            fields.put(colModifiedBy.getName(), user.getUserId());

        ColumnInfo colModified = table.getColumn(MODIFIED_COLUMN_NAME);
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

        if (fields.containsKey(MODIFIED_BY_COLUMN_NAME))
            _setProperty(returnObject, MODIFIED_BY_COLUMN_NAME, fields.get(MODIFIED_BY_COLUMN_NAME));

        if (fields.containsKey(MODIFIED_COLUMN_NAME))
            _setProperty(returnObject, MODIFIED_COLUMN_NAME, fields.get(MODIFIED_COLUMN_NAME));

        ColumnInfo colModified = table.getColumn(MODIFIED_COLUMN_NAME);
        ColumnInfo colVersion = table.getVersionColumn();
        if (null != colVersion && colVersion != colModified && colVersion.getJdbcType() == JdbcType.TIMESTAMP)
            _setProperty(returnObject, colVersion.getName(), fields.get(colVersion.getName()));
    }


    static private void _setProperty(Object fields, String propName, Object value)
    {
        if (fields instanceof Map)
        {
            ((Map<String, Object>) fields).put(propName, value);
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


    /**
     * @return a new Map&lt;String, Object&gt; if fieldsIn is a Map, otherwise returns modified version of fieldsIn.
     * @throws RuntimeValidationException if there is a problem with the data that's detected before we try to actually do the insert
     * @throws RuntimeSQLException if there is a problem communicating with the database or there is a constraint violation or similar error
     */
    public static <K> K insert(@Nullable User user, TableInfo table, K fieldsIn)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): ("Table " + table.getSchema().getName() + "." + table.getName() + " is not in the physical database.");

        // _executeTriggers(table, fields);

        SQLFragment insertSQL = new SQLFragment();
        StringBuilder columnSQL = new StringBuilder();
        StringBuilder valueSQL = new StringBuilder();
        ColumnInfo autoIncColumn = null;
        ColumnInfo versionColumn = null;
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
            // note  if(version) is not an no-op, it protects the isautoinc test from version columns implemented using sequences
            if (column.isVersionColumn())
                versionColumn = column;
            else if (column.isAutoIncrement())
                autoIncColumn = column;

            if (!fields.containsKey(column.getName()))
                continue;

            Object value = fields.get(column.getName());

            if (!column.isAutoIncrement() &&
                    column.isRequired() &&
                    (null == value || value instanceof String && 0 == ((String) value).length()) &&
                    !Table.AUTOPOPULATED_COLUMN_NAMES.contains(column.getName()) &&
                    column.getJdbcDefaultValue() == null)
            {
                throw new RuntimeValidationException("A value is required for field '" + column.getName() + "'", column.getName());
            }

            columnSQL.append(comma);
            columnSQL.append(column.getSelectName());
            valueSQL.append(comma);
            if (null == value || value instanceof String && 0 == ((String) value).length())
                valueSQL.append("NULL");
            else
            {
                // Validate the value
                List<ColumnValidator> validators = ColumnValidators.create(column, null);
                for (ColumnValidator v : validators)
                {
                    String msg = v.validate(1, value);
                    if (msg != null)
                        throw new RuntimeValidationException(msg, column.getName()); // CONSIDER: would prefer throwing ValidationException instead, but it's not a RuntimeException
                }

                valueSQL.append('?');
                if (value instanceof Parameter.JdbcParameterValue)
                    insertSQL.add(value);
                else
                    insertSQL.add(new Parameter.TypedValue(value, column.getJdbcType()));
            }
            comma = ", ";
        }

        if (comma.length() == 0)
        {
            // NO COLUMNS TO INSERT
            throw new IllegalArgumentException("Table.insert called with no column data. table=" + table + " object=" + String.valueOf(fieldsIn));
        }

        insertSQL.append("INSERT INTO ");
        insertSQL.append(table.getSelectName());
        insertSQL.append("\n\t(");
        insertSQL.append(columnSQL);
        insertSQL.append(")\n\t");
        insertSQL.append("VALUES (");
        insertSQL.append(valueSQL);
        insertSQL.append(')');

        // CONSIDER reselect version column
        if (null != autoIncColumn)
            table.getSqlDialect().addReselect(insertSQL, autoIncColumn, null);

        // If Map was handed in, then we hand back a Map
        // UNDONE: use Table.select() to reselect and return new Object

        //noinspection unchecked
        K returnObject = (K) (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap) ? fields : fieldsIn);

        DbSchema schema = table.getSchema();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try (Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
        {
            conn = schema.getScope().getConnection();
            stmt = prepareStatement(conn, insertSQL.getSQL(), insertSQL.getParams(), jdbcParameters);

            if (null == autoIncColumn)
            {
                stmt.execute();
            }
            else
            {
                rs = table.getSqlDialect().executeWithResults(stmt);
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
            logException(insertSQL, conn, e, Level.WARN);
            throw new RuntimeSQLException(e);
        }
        finally
        {
            doClose(rs, stmt, conn, schema.getScope());
        }

        return returnObject;
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals)
    {
        return update(user, table, fieldsIn, pkVals, null, Level.WARN);
    }


    public static <K> K update(@Nullable User user, TableInfo table, K fieldsIn, Object pkVals, @Nullable Filter filter, Level level)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        assert null != pkVals;

        // _executeTriggers(table, previous, fields);

        StringBuilder setSQL = new StringBuilder();
        StringBuilder whereSQL = new StringBuilder();
        ArrayList<Object> parametersSet = new ArrayList<>();
        ArrayList<Object> parametersWhere = new ArrayList<>();
        String comma = "";

        // UNDONE -- rowVersion
        List<ColumnInfo> columnPK = table.getPkColumns();

        // Name-value pairs for the PK columns for this row
        Map<String, Object> keys = new CaseInsensitiveHashMap<>();

        if (columnPK.size() == 1 && !pkVals.getClass().isArray())
            keys.put(columnPK.get(0).getName(), pkVals);
        else if (pkVals instanceof Map)
            keys.putAll((Map<? extends String, ?>)pkVals);
        else
        {
            Object[] pkValueArray = (Object[]) pkVals;
            if (pkValueArray.length != columnPK.size())
            {
                throw new IllegalArgumentException("Expected to get " + columnPK.size() + " key values, but got " + pkValueArray.length);
            }
            // Assume that the key values are in the same order as the key columns returned from getPkColumns()
            for (int i = 0; i < columnPK.size(); i++)
            {
                keys.put(columnPK.get(i).getName(), pkValueArray[i]);
            }
        }

        String whereAND = "WHERE ";

        if (null != filter)
        {
            SQLFragment fragment = filter.getSQLFragment(table, null);
            whereSQL.append(fragment.getSQL());
            parametersWhere.addAll(fragment.getParams());
            whereAND = " AND ";
        }

        for (ColumnInfo col : columnPK)
        {
            whereSQL.append(whereAND);
            whereSQL.append(col.getSelectName());
            whereSQL.append("=?");
            parametersWhere.add(keys.get(col.getName()));
            whereAND = " AND ";
        }

        //noinspection unchecked
        Map<String, Object> fields = fieldsIn instanceof Map ?
            _getTableData(table, (Map<String,Object>)fieldsIn, true) :
            _getTableData(table, fieldsIn, true);
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        _updateSpecialFields(user, table, fields, date);

        List<ColumnInfo> columns = table.getColumns();
        ColumnInfo colModified = table.getColumn(MODIFIED_COLUMN_NAME);

        // Issue 22873: user could attempt to updateRow with no columns in the table; avoid SQL error and log warning
        boolean hasFieldsToSet = false;
        for (ColumnInfo column : columns)
        {
            if (column.isVersionColumn() && column != colModified)
            {
                SQLFragment expr = column.getVersionUpdateExpression();
                if (null != expr)
                {
                    setSQL.append(comma);
                    setSQL.append(column.getSelectName());
                    setSQL.append("=");
                    setSQL.append(expr);
                    comma = ", ";
                    hasFieldsToSet = true;
                    continue;
                }
            }

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
                // Validate the value
                List<ColumnValidator> validators = ColumnValidators.create(column, null);
                for (ColumnValidator v : validators)
                {
                    String msg = v.validate(1, value);
                    if (msg != null)
                        throw new RuntimeValidationException(msg, column.getName()); // CONSIDER: would prefer throwing ValidationException instead, but it's not a RuntimeException
                }

                setSQL.append("=?");
                if (value instanceof Parameter.JdbcParameterValue)
                    parametersSet.add(value);
                else
                    parametersSet.add(new Parameter.TypedValue(value, column.getJdbcType()));
            }

            comma = ", ";
            hasFieldsToSet = true;
        }

        if (hasFieldsToSet)
        {
            // UNDONE: reselect
            SQLFragment updateSQL = new SQLFragment("UPDATE " + table.getSelectName() + "\n\t" +
                                                    "SET " + setSQL + "\n\t" +
                                                    whereSQL);

            updateSQL.addAll(parametersSet);
            updateSQL.addAll(parametersWhere);

            try
            {
                int count = new SqlExecutor(table.getSchema()).execute(updateSQL);

                // check for concurrency problem
                if (count == 0)
                {
                    throw OptimisticConflictException.create(ERROR_DELETED);
                }

                _copyUpdateSpecialFields(table, fieldsIn, fields);
                notifyTableUpdate(table);
            }
            catch (OptimisticConflictException e)
            {
                logException(updateSQL, null, e.getSQLException(), level);
                throw (e);
            }
        }
        else
            _log.warn("Attempt to update table '" + table.getName() + "' with no valid fields.");

        return (fieldsIn instanceof Map && !(fieldsIn instanceof BoundMap)) ? (K)fields : fieldsIn;
    }


    public static void delete(TableInfo table, Object rowId)
    {
        List<ColumnInfo> columnPK = table.getPkColumns();
        Object[] pkVals;

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

    public static int delete(TableInfo table)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        SqlExecutor sqlExecutor = new SqlExecutor(table.getSchema());
        int result = sqlExecutor.execute("DELETE FROM " + table.getSelectName());
        notifyTableUpdate(table);
        return result;
    }

    public static int delete(TableInfo table, Filter filter)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");

        SQLFragment where = filter.getSQLFragment(table, null);

        String deleteSQL = "DELETE FROM " + table.getSelectName() + "\n\t" + where.getSQL();
        int result = new SqlExecutor(table.getSchema()).execute(deleteSQL, where.getParams().toArray());

        notifyTableUpdate(table);
        return result;
    }

    public static void truncate(TableInfo table)
    {
        assert (table.getTableType() != DatabaseTableType.NOT_IN_DB): (table.getName() + " is not in the physical database.");
        SqlExecutor sqlExecutor = new SqlExecutor(table.getSchema());
        sqlExecutor.execute(table.getSqlDialect().getTruncateSql(table.getSelectName()));
        notifyTableUpdate(table);
    }


    public static SQLFragment getSelectSQL(TableInfo table, @Nullable Collection<ColumnInfo> columns, @Nullable Filter filter, @Nullable Sort sort)
    {
        return QueryService.get().getSelectSQL(table, columns, filter, sort, ALL_ROWS, NO_OFFSET, false);
    }


    public static void ensureRequiredColumns(TableInfo table, Map<String, ColumnInfo> cols, @Nullable Filter filter, @Nullable Sort sort, @Nullable List<Aggregate> aggregates)
    {
        List<ColumnInfo> allColumns = table.getColumns();
        Set<FieldKey> requiredColumns = new HashSet<>();

        if (null != filter)
            requiredColumns.addAll(filter.getWhereParamFieldKeys());

        if (null != sort)
        {
            requiredColumns.addAll(sort.getRequiredColumns(cols));
        }

        if (null != aggregates)
        {
            for (Aggregate agg : aggregates)
                requiredColumns.add(agg.getFieldKey());
        }

        // TODO: Ensure pk, filter & where columns in cases where caller is naive

        for (ColumnInfo column : allColumns)
        {
            if (cols.containsKey(column.getAlias()))
                continue;
            if (requiredColumns.contains(column.getFieldKey()) || requiredColumns.contains(new FieldKey(null,column.getAlias())) || requiredColumns.contains(new FieldKey(null,column.getPropertyName())))
                cols.put(column.getAlias(), column);
            else if (column.isKeyField())
                cols.put(column.getAlias(), column);
            else if (column.isVersionColumn())
                cols.put(column.getAlias(), column);
        }
    }


    public static void snapshot(TableInfo tinfo, String tableName) throws SQLException
    {
        SQLFragment sqlSelect = getSelectSQL(tinfo, null, null, null);
        SQLFragment sqlSelectInto = new SQLFragment();
        sqlSelectInto.append("SELECT * INTO ").append(tableName).append(" FROM (");
        sqlSelectInto.append(sqlSelect);
        sqlSelectInto.append(") _from_");

        new SqlExecutor(tinfo.getSchema()).execute(sqlSelectInto);
    }


    // Table modification

    public static void notifyTableUpdate(/*String operation,*/ TableInfo table/*, Container c*/)
    {
        DbCache.invalidateAll(table);
    }


    private static void _logQuery(Level level, @Nullable SQLFragment sqlFragment, @Nullable Connection conn)
    {
        if (!_log.isEnabledFor(level) || null == sqlFragment)
            return;

        String sql = sqlFragment.getSQL();
        Object[] parameters = sqlFragment.getParamsArray();

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

    
    public static class TestCase extends Assert
    {
        private static final CoreSchema _core = CoreSchema.getInstance();

        public static class Principal
        {
            private int _userId;
            private String _ownerId;
            private String _type;
            private String _name;


            public int getUserId()
            {
                return _userId;
            }


            public void setUserId(int userId)
            {
                _userId = userId;
            }


            public String getOwnerId()
            {
                return _ownerId;
            }


            public void setOwnerId(String ownerId)
            {
                _ownerId = ownerId;
            }


            public String getType()
            {
                return _type;
            }


            public void setType(String type)
            {
                _type = type;
            }


            public String getName()
            {
                return _name;
            }


            public void setName(String name)
            {
                _name = name;
            }
        }


        @Test
        public void testSelect() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            //noinspection EmptyTryBlock,UnusedDeclaration
            try (ResultSet rs = new TableSelector(tinfo).getResultSet()){}

            Map[] maps = new TableSelector(tinfo).getMapArray();
            assertNotNull(maps);

            Principal[] principals = new TableSelector(tinfo).getArray(Principal.class);
            assertNotNull(principals);
            assertTrue(principals.length > 0);
            assertTrue(principals[0]._userId != 0);
            assertNotNull(principals[0]._name);
            assertEquals(maps.length, principals.length);
        }


        @Test
        public void testMaxRows() throws SQLException
        {
            TableInfo tinfo = _core.getTableInfoPrincipals();

            int maxRows;

            try (Results rsAll = new TableSelector(tinfo).getResults())
            {
                rsAll.last();
                maxRows = rsAll.getRow();
                assertTrue(rsAll.isComplete());
            }

            maxRows -= 2;

            try (Results rs = new TableSelector(tinfo).setMaxRows(maxRows).getResults())
            {
                rs.last();
                int row = rs.getRow();
                assertTrue(row == maxRows);
                assertFalse(rs.isComplete());
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
            DbSchema core = CoreSchema.getInstance().getSchema();
            SqlDialect dialect = core.getScope().getSqlDialect();
            
            String name = dialect.getTempTablePrefix() + "_" + GUID.makeHash();
            Connection conn = core.getScope().getConnection();
            assertTrue(conn != null);
            
            try
            {
                PreparedStatement stmt = conn.prepareStatement("CREATE " + dialect.getTempTableKeyword() + " TABLE " + name +
                        "(s VARCHAR(36), d " + dialect.sqlTypeNameFromJdbcType(JdbcType.TIMESTAMP) + ")");
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
                try
                {
                    PreparedStatement cleanup = conn.prepareStatement("DROP TABLE " + name);
                    cleanup.execute();
                }
                finally
                {
                    conn.close();
                }
            }
        }


        @Test
        public void testAggregates() throws SQLException
        {
            TableInfo tinfo = CoreSchema.getInstance().getTableInfoContainers();
            List<Aggregate> aggregates = new LinkedList<>();

            // Test no aggregates case
            Map<String, List<Aggregate.Result>> aggregateMap = new TableSelector(tinfo, Collections.emptyList(), null, null).getAggregates(aggregates);
            assertTrue(aggregateMap.isEmpty());

            aggregates.add(Aggregate.createCountStar());
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.BaseType.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.BaseType.SUM));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.BaseType.MEAN));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.BaseType.MIN));
            aggregates.add(new Aggregate(tinfo.getColumn("RowId"), Aggregate.BaseType.MAX));
            aggregates.add(new Aggregate(tinfo.getColumn("Parent"), Aggregate.BaseType.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("Parent").getFieldKey(), Aggregate.BaseType.COUNT, null, true));
            aggregates.add(new Aggregate(FieldKey.fromParts("Parent", "Parent"), Aggregate.BaseType.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn("SortOrder"), Aggregate.BaseType.SUM));
            aggregates.add(new Aggregate(tinfo.getColumn("SortOrder").getFieldKey(), Aggregate.BaseType.SUM, null, true));
            aggregates.add(new Aggregate(tinfo.getColumn(CREATED_BY_COLUMN_NAME), Aggregate.BaseType.COUNT));
            aggregates.add(new Aggregate(tinfo.getColumn(CREATED_COLUMN_NAME), Aggregate.BaseType.MIN));
            aggregates.add(new Aggregate(tinfo.getColumn("Name"), Aggregate.BaseType.MIN));

            aggregateMap = new TableSelector(tinfo, Collections.emptyList(), null, null).getAggregates(aggregates);

            String sql =
                    "SELECT " +
                        "CAST(COUNT(*) AS BIGINT) AS CountStar,\n" +
                        "CAST(COUNT(C.RowId) AS BIGINT) AS CountRowId,\n" +
                        "CAST(SUM(C.RowId) AS BIGINT) AS SumRowId,\n" +
                        "AVG(C.RowId) AS AvgRowId,\n" +
                        "CAST(MIN(C.RowId) AS BIGINT) AS MinRowId,\n" +
                        "CAST(MAX(C.RowId) AS BIGINT) AS MaxRowId,\n" +
                        "CAST(COUNT(C.Parent) AS BIGINT) AS CountParent,\n" +
                        "CAST(COUNT(DISTINCT C.Parent) AS BIGINT) AS CountDistinctParent,\n" +
                        "CAST(COUNT(P.Parent) AS BIGINT) AS CountParent_fs_Parent,\n" +
                        "CAST(SUM(C.SortOrder) AS BIGINT) AS SumSortOrder,\n" +
                        "CAST(SUM(DISTINCT C.SortOrder) AS BIGINT) AS SumDistinctSortOrder,\n" +
                        "CAST(COUNT(C.CreatedBy) AS BIGINT) AS CountCreatedBy,\n" +
                        "MIN(C.Created) AS MinCreated,\n" +
                        "MIN(C.Name) AS MinName\n" +
                    "FROM core.Containers C\n" +
                    "LEFT OUTER JOIN core.Containers P ON C.parent = P.entityid\n";
            Map<String, Object> expected = new SqlSelector(tinfo.getSchema(), sql).getMap();

            verifyAggregates(expected, aggregateMap);
        }


        private void verifyAggregates(Map<String, Object> expected, Map<String, List<Aggregate.Result>> aggregateMap)
        {
            verifyAggregate(expected.get("CountStar"), aggregateMap.get("*").get(0).getValue());

            verifyAggregate(expected.get("CountRowId"), aggregateMap.get("RowId").get(0).getValue());
            verifyAggregate(expected.get("SumRowId"), aggregateMap.get("RowId").get(1).getValue());
            verifyAggregate(expected.get("AvgRowId"), aggregateMap.get("RowId").get(2).getValue());
            verifyAggregate(expected.get("MinRowId"), aggregateMap.get("RowId").get(3).getValue());
            verifyAggregate(expected.get("MaxRowId"), aggregateMap.get("RowId").get(4).getValue());

            verifyAggregate(expected.get("CountParent"), aggregateMap.get("Parent").get(0).getValue());
            verifyAggregate(expected.get("CountDistinctParent"), aggregateMap.get("Parent").get(1).getValue());

            verifyAggregate(expected.get("CountParent_fs_Parent"), aggregateMap.get("Parent/Parent").get(0).getValue());

            verifyAggregate(expected.get("SumSortOrder"), aggregateMap.get("SortOrder").get(0).getValue());
            verifyAggregate(expected.get("SumDistinctSortOrder"), aggregateMap.get("SortOrder").get(1).getValue());

            verifyAggregate(expected.get("CountCreatedBy"), aggregateMap.get(CREATED_BY_COLUMN_NAME).get(0).getValue());
            verifyAggregate(expected.get("MinCreated"), aggregateMap.get(CREATED_COLUMN_NAME).get(0).getValue());
            verifyAggregate(expected.get("MinName"), aggregateMap.get("Name").get(0).getValue());
        }


        private void verifyAggregate(Object expected, Object actual)
        {
            // Address AVG on SQL Server... expected query returns Integer type but aggregate converts to Long
            if (expected.getClass() != actual.getClass())
                assertEquals(((Number)expected).longValue(), ((Number)actual).longValue());
            else
                assertEquals(expected, actual);
        }
    }


    static public Map<FieldKey, ColumnInfo> createColumnMap(@Nullable TableInfo table, @Nullable Collection<ColumnInfo> columns)
    {
        Map<FieldKey, ColumnInfo> ret = new HashMap<>();
        if (columns != null)
        {
            for (ColumnInfo column : columns)
            {
                ret.put(column.getFieldKey(), column);
            }
        }
        if (table != null)
        {
            for (String name : table.getColumnNameSet())
            {
                FieldKey f = FieldKey.fromParts(name);
                if (ret.containsKey(f))
                    continue;
                ColumnInfo column = table.getColumn(name);
                if (column != null)
                {
                    ret.put(column.getFieldKey(), column);
                }
            }
        }
        return ret;
    }


    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix)
    {
        return checkAllColumns(table, columns, prefix, false);
    }

    public static boolean checkAllColumns(TableInfo table, Collection<ColumnInfo> columns, String prefix, boolean enforceUnique)
    {
        int bad = 0;

//        Map<FieldKey, ColumnInfo> mapFK = new HashMap<>(columns.size()*2);
        Map<String, ColumnInfo> mapAlias = new HashMap<>(columns.size()*2);
        ColumnInfo prev;

        for (ColumnInfo column : columns)
        {
            if (!checkColumn(table, column, prefix))
                bad++;
//            if (enforceUnique && null != (prev=mapFK.put(column.getFieldKey(), column)) && prev != column)
//                bad++;
            if (enforceUnique && null != (prev=mapAlias.put(column.getAlias(),column)) && prev != column)
                bad++;
        }

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
        if (column.getParentTable() != table)
        {
            _log.warn(prefix + ": Column " + column + " is from the wrong table: " + column.getParentTable() + " instead of " + table);
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
        if (null == table.getMetaDataName())
            throw new IllegalArgumentException();

        SqlDialect d = tableDelete.getSqlDialect();

        List<ColumnInfo> columnPK = table.getPkColumns();
        List<Parameter> paramPK = new ArrayList<>(columnPK.size());
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

        return new Parameter.ParameterMap(tableDelete.getSchema().getScope(), conn, sqlfDelete, updatable.remapSchemaColumns());
    }


    public static class TestDataIterator extends AbstractDataIterator
    {
        private final String guid = GUID.makeGUID();
        private final Date date = new Date();

        // TODO: guid values are ignored, since guidCallable gets used instead
        private final Object[][] _data = new Object[][]
        {
            new Object[] {1, "One", 101, true, date, guid},
            new Object[] {2, "Two", 102, true, date, guid},
            new Object[] {3, "Three", 103, true, date, guid}
        };

        private final static class _ColumnInfo extends ColumnInfo
        {
            _ColumnInfo(String name, JdbcType type)
            {
                super(name, type);
                setReadOnly(true);
            }
        }

        private final static ColumnInfo[] _cols = new ColumnInfo[]
        {
            new _ColumnInfo("_row", JdbcType.INTEGER),
            new _ColumnInfo("Text", JdbcType.VARCHAR),
            new _ColumnInfo("IntNotNull", JdbcType.INTEGER),
            new _ColumnInfo("BitNotNull", JdbcType.BOOLEAN),
            new _ColumnInfo("DateTimeNotNull", JdbcType.TIMESTAMP),
            new _ColumnInfo(ENTITY_ID_COLUMN_NAME, JdbcType.VARCHAR)
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
            TableInfo testTable = TestSchema.getInstance().getTableInfoTestTable();

            DataIteratorContext dic = new DataIteratorContext();
            TestDataIterator extract = new TestDataIterator();
            SimpleTranslator translate = new SimpleTranslator(extract, dic);
            translate.selectAll();
            translate.addBuiltInColumns(dic, JunitUtil.getTestContainer(), TestContext.get().getUser(), testTable, false);

            DataIteratorBuilder load = TableInsertDataIterator.create(
                    translate,
                    testTable,
                    dic
            );
            new Pump(load, dic).run();

            assertFalse(dic.getErrors().hasErrors());

            new SqlExecutor(testTable.getSchema()).execute("DELETE FROM test.testtable WHERE EntityId = '" + extract.guid + "'");
        }
    }
}
