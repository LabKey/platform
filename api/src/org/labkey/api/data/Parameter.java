/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import com.google.common.primitives.Ints;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Parameter is bound to a particular parameter of a particular PreparedStatement
 *
 * TypedValue is useful for dragging along a sqlType with a raw value, usually this is not necessary as
 * type can be inferred.  However, this can be useful for NULL parameters and
 * for distinguishing unicode, non-unicode types
 *
 * NOTE: jdbc does not have separate Type values for varchar and nvarchar
 * NOTE: does not do implicit conversion, just sets the parameter type
 *
 * User: matthewb
 * Date: Feb 13, 2007
 */

public class Parameter implements AutoCloseable
{
    private static Logger LOG = Logger.getLogger(Parameter.class);

    public interface JdbcParameterValue
    {
        @Nullable
        Object getJdbcParameterValue();
        @NotNull
        JdbcType getJdbcParameterType();
    }

    public static class TypedValue implements JdbcParameterValue
    {
        private final Object _value;
        private final JdbcType _type;

        public TypedValue(Object value, JdbcType type)
        {
            _value = value;
            _type = type;
        }

        @Override
        public Object getJdbcParameterValue()
        {
            return _value;
        }

        @Override @NotNull
        public JdbcType getJdbcParameterType()
        {
            return _type;
        }

        public String toString()
        {
            return String.valueOf(_value);
        }
    }


    public static Object NULL_MARKER = new TypedValue(null, JdbcType.NULL)
    {
        @Override
        public String toString()
        {
            return "NULL";
        }
    };

    
    public static TypedValue nullParameter(JdbcType jdbcType)
    {
        return new TypedValue(null, jdbcType);
    }


    private String _name;
    private @Nullable String _uri = null;       // for migration of ontology based code
    private final @Nullable JdbcType _type;
    private boolean setFileAsName = false;

    // only allow setting once, do not clear
    private boolean _constant = false;

    private PreparedStatement _stmt;
    private AutoCloseable _autoCloseable;
    private int[] _indexes;

    // used to to optimize calls to Statement.set*()
    private boolean _isSet = false;
    private boolean _isNull = false;


    public Parameter(PreparedStatement stmt, int index)
    {
        this(stmt, new int[] { index }, null);
    }

    public Parameter(PreparedStatement stmt, int index, JdbcType type)
    {
        this(stmt, new int[] { index }, type);
    }

    public Parameter(PreparedStatement stmt, int[] indexes, @Nullable JdbcType type)
    {
        _stmt = stmt;
        _indexes = indexes;
        _type = type;
    }

    public Parameter(String name, int index, JdbcType type)
    {
        this(name, null, new int[] { index }, type);
    }


    // this constructor should be used with new ParameterMap(SQLFragment) to compute indexes
    public Parameter(String name, JdbcType type)
    {
        this(name, null, null, type);
    }


    public Parameter(String name, int[] indexes, JdbcType type)
    {
        this(name, null, indexes, type);
    }

    public Parameter(String name, String uri, int index, JdbcType type)
    {
        this(name, uri, new int[] { index }, type);
    }

    public Parameter(String name, @Nullable String uri, @Nullable int[] indexes, JdbcType type)
    {
        _name = name;
        _uri = uri;
        _indexes = null == indexes ? new int[0] : indexes;
        _type = type;
    }

    public Parameter(ColumnInfo c, int index)
    {
        this(c, new int[] { index });
    }

    public Parameter(ColumnInfo c, int[] indexes)
    {
        // The jdbc resultset metadata replaces special characters in source column names.
        // We need the parameter names to match so we can match to the column.
        _name = c.getJdbcRsName();
        _uri = c.getPropertyURI();
        _type = c.getJdbcType();
        _indexes = indexes;
        setFileAsName = (c.getInputType().equalsIgnoreCase("file") && _type == JdbcType.VARCHAR);
    }


    public Parameter copy(PreparedStatement stmt)
    {
        Parameter copy = new Parameter(this._name, this._uri, this._indexes, this._type);
        // not actually using constant parameters yet, if we were we'd have to remember the value so we could copy it
        if (_constant)
            throw new IllegalStateException("Copying constant parameters is not yet implemented");
        copy._stmt = stmt;
        return copy;
    }


    public void setName(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public @Nullable JdbcType getType()
    {
        return _type;
    }

//    public void setValue(Object in, boolean constant) throws SQLException
//    {
//        setValue(in);
//        _constant = constant;
//    }

    public void setValue(@Nullable Object in) throws SQLException
    {
        if (_constant)
            throw new IllegalStateException("Can't set constant parameter");

        JdbcType type = _type;
        if (null == type)
        {
            if (in instanceof JdbcParameterValue)
                type = ((JdbcParameterValue)in).getJdbcParameterType();
        }

        Object value = getValueToBind(in, type);

        try
        {
            if (null == value)
            {
                if (!_isNull)
                    setNull(type);
                _isSet = true;
                _isNull = true;
                return;
            }

            if (value instanceof AttachmentFile)
            {
                if (_indexes.length > 1)
                    throw new IllegalArgumentException("AttachmentFile can only be bound to a single parameter");

                final AttachmentFile attachmentFile = (AttachmentFile) value;

                // Set up to close it
                _autoCloseable = () -> {
                    try
                    {
                        attachmentFile.closeInputStream();
                    }
                    catch (IOException ignored) {}
                };

                if (setFileAsName)
                {
                    value = attachmentFile.getFilename();
                    _stmt.setString(_indexes[0], (String)value);
                }
                else
                {
                    try
                    {
                        InputStream is = attachmentFile.openInputStream();
                        long len = attachmentFile.getSize();

                        if (len > Integer.MAX_VALUE)
                            throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
                        _stmt.setBinaryStream(_indexes[0], is, (int)len);
                        _isSet = true;
                        _isNull = false;
                        return;
                    }
                    catch (Exception x)
                    {
                        SQLException sqlx = new SQLException();
                        sqlx.initCause(x);
                        throw sqlx;
                    }
                }
            }

            if (value instanceof Collection)
            {
                value = ((Collection)value).toArray();
            }

            if (value instanceof Object[])
            {
                // Delegate dialect-specific details of JDBC array creation and type inference to the LabKey ConnectionWrapper,
                // which knows the current dialect.
                final Array jdbcArray = _stmt.getConnection().createArrayOf(null, (Object[]) value);
                // Set up to close it
                _autoCloseable = jdbcArray::free;
                value = jdbcArray;
            }

            setObject(type, value);
        }
        catch (SQLException e)
        {
            LOG.error("Exception converting \"" + value + "\" to type " + _type);
            throw e;
        }
    }

    @Override
    public void close()
    {
        if (_autoCloseable != null)
        {
            try
            {
                _autoCloseable.close();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            catch (Exception e)
            {
                throw new UnexpectedException(e);
            }
        }
    }

    private void setNull(JdbcType type) throws SQLException
    {
        for (int index : _indexes)
            _stmt.setNull(index, null==type ? JdbcType.VARCHAR.sqlType : type.sqlType);
        _isSet = true;
        _isNull = true;
    }


    private void setObject(JdbcType type, Object value) throws SQLException
    {
        if (null == type)
            for (int index : _indexes)
                _stmt.setObject(index, value);
        else
            for (int index : _indexes)
                _stmt.setObject(index, value, type.sqlType== Types.TINYINT?Types.SMALLINT:type.sqlType);
        _isSet = true;
        _isNull = (value == null);
    }

    
    // TODO: Switching to BeanUtils 1.8 (which has two-way converters) should let us clean this up significantly by
    // delegating most of the binding to our converters
    public static Object getValueToBind(@Nullable Object value, @Nullable JdbcType type) throws SQLException
    {
        if (value instanceof Callable)
        {
            try
            {
                value = ((Callable)value).call();
            }
            catch (SQLException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }

        if (value instanceof QueryService.ParameterDecl)
        {
            QueryService.ParameterDecl decl = (QueryService.ParameterDecl)value;
            if (decl.isRequired())
                throw new QueryService.NamedParameterNotProvided(decl.getName());
            value = decl.getDefault();
        }

        if (value instanceof JdbcParameterValue)
        {
            value = ((JdbcParameterValue)value).getJdbcParameterValue();
            // don't really want this to happen, but value could get double wrapped
            if (value instanceof JdbcParameterValue)
                value = ((JdbcParameterValue)value).getJdbcParameterValue();
        }

        if (value == null)
            return null;

        if (value instanceof Double)
            return ResultSetUtil.mapJavaDoubleToDatabaseDouble(((Double)value));
        if (value instanceof Number || value instanceof String)
            return value;
        else if (value instanceof java.util.Date)
        {
            if (!(value instanceof java.sql.Date) && !(value instanceof java.sql.Time) && !(value instanceof java.sql.Timestamp))
                return new java.sql.Timestamp(((java.util.Date) value).getTime());
        }
        else if (value instanceof AttachmentFile)
            return value;
        else if (value instanceof GregorianCalendar)
            return new java.sql.Timestamp(((java.util.GregorianCalendar) value).getTimeInMillis());
        else if (value.getClass() == java.lang.Character.class || value instanceof CharSequence)
            return value.toString();
        else if (value instanceof Enum && type != null && type.isNumeric())
            return ((Enum)value).ordinal();
        else if (value instanceof Enum)
            return ((Enum)value).name();
        else if (value instanceof Class)
            return (((Class) value).getName());
        else if (value instanceof Lsid)
            return value.toString();

        return value;
    }



    public String toString()
    {
        return "[" + (null==_indexes?"":Ints.join(",", _indexes)) + (null==_name?"":":"+_name) + "]";
    }


    public static class ParameterMap implements AutoCloseable
    {
        SQLFragment _sqlf;
        PreparedStatement _stmt;
        boolean _selectRowId = false;
        Integer _selectObjectIdIndex = null;
        Integer _rowId;
        Integer _objectId;
        CaseInsensitiveHashMap<Integer> _map;
        Parameter[] _parameters;
        DbScope _scope;
        Connection _conn;       // only used for copy()
        SqlDialect _dialect;


        public ParameterMap(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters)
        {
            this(scope, stmt, parameters, null);
        }


        public ParameterMap(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
        {
            init(scope, stmt, parameters, remap);
        }


        public ParameterMap copy() throws SQLException
        {
            if (null == _sqlf || null == _conn)
                throw new IllegalStateException("Copy can only be used on ParameterMap constructed with SQL");
            return new ParameterMap(this);
        }


        private ParameterMap(ParameterMap from) throws SQLException
        {
            _sqlf = from._sqlf;
            _scope = from._scope;
            _conn = from._conn;
            if (_sqlf.getSQL().startsWith("{call"))
                _stmt = _conn.prepareCall(_sqlf.getSQL());
            else
                _stmt= _conn.prepareStatement(_sqlf.getSQL());

            _selectRowId = from._selectRowId;
            _selectObjectIdIndex = from._selectObjectIdIndex;
            _rowId = from._rowId;
            _objectId = from._objectId;
            _dialect = from._dialect;
            _map = from._map;
            _parameters = new Parameter[from._parameters.length];
            for (int i=0 ; i<from._parameters.length ; i++)
                _parameters[i] = from._parameters[i].copy(_stmt);
        }


        public ParameterMap(DbScope scope, SQLFragment sql, Map<String, String> remap) throws SQLException
        {
            this(scope, scope.getConnection(), sql, remap);
        }


        /**
         *  sql bound to constants or Parameters, compute the index array for each named Parameter
         */
        public ParameterMap(DbScope scope, Connection conn, SQLFragment sql, Map<String, String> remap) throws SQLException
        {
            _sqlf = sql;
            _conn = conn;
            PreparedStatement stmt;
            if (_sqlf.getSQL().startsWith("{call"))
                stmt = conn.prepareCall(_sqlf.getSQL());
            else
                stmt= conn.prepareStatement(_sqlf.getSQL());

            IdentityHashMap<Parameter, IntegerArray> paramMap = new IdentityHashMap<>();
            List<Object> paramList = _sqlf.getParams();
            List<Parameter> parameters = new ArrayList<>(paramList.size());

            for (int i = 0; i < paramList.size(); i++)
            {
                Object o = paramList.get(i);
                if (!(o instanceof Parameter))
                {
                    new Parameter(stmt, i).setValue(o);
                    continue;
                }
                Parameter p = (Parameter)o;
                if (!paramMap.containsKey(p))
                    paramMap.put(p, new IntegerArray());
                paramMap.get(p).add(i+1);
            }

            for (Map.Entry<Parameter, IntegerArray> e : paramMap.entrySet())
            {
                e.getKey()._indexes = e.getValue().toArray(null);
                parameters.add(e.getKey());
            }

            init(scope, stmt, parameters, remap);
        }


        private void init(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
        {
            _scope = scope;
            _dialect = scope.getSqlDialect();
            _map = new CaseInsensitiveHashMap<>(parameters.size() * 2);
            _parameters = parameters.toArray(new Parameter[parameters.size()]);
            _stmt = stmt;

            for (int i=0 ; i<_parameters.length ; i++)
            {
                Parameter p = _parameters[i];
                if (null == p._name)
                    throw new IllegalStateException();
                p._stmt = stmt;
                String name = p._name;
                if (null != remap && remap.containsKey(name))
                    name = remap.get(name);
                if (_map.containsKey(name))
                    throw new IllegalArgumentException("duplicate parameter name: " + name);
                _map.put(name, i);
                if (null != p._uri)
                {
                    String uri = p._uri;
                    if (null != remap && remap.containsKey(uri))
                        uri = remap.get(uri);
                    if (_map.containsKey(uri))
                        throw new IllegalArgumentException("duplicate property uri: " + uri);
                    _map.put(uri, i);
                }
            }
        }


        public void setSelectRowId(boolean selectRowId)
        {
            _selectRowId = selectRowId;
        }

        public void setObjectIdIndex(Integer i)
        {
            _selectObjectIdIndex = i;
        }

        public boolean hasReselectRowId()
        {
            return _selectRowId;
        }

        public boolean hasReselectObjectId()
        {
            return null != _objectId;
        }

        public int size()
        {
            return _map.size();
        }


        public boolean containsKey(String name)
        {
            return _map.containsKey(name);
        }


        public Parameter getParameter(String name)
        {
            Integer i = _map.get(name);
            return null==i ? null : _parameters[i];
        }
        

        public void executeBatch() throws SQLException
        {
            prepareParametersBeforeExecute();

            _objectId = null;
            _rowId = null;
            _stmt.executeBatch();
        }


        public boolean execute() throws SQLException
        {
            prepareParametersBeforeExecute();

            ResultSet rs = null;
            _rowId = null;
            _objectId = null;

            try
            {
                if (_selectRowId || _selectObjectIdIndex != null)
                    rs = _dialect.executeWithResults(_stmt);
                else
                    _stmt.execute();

                Integer firstInt = null, secondInt = null;

                if (null != rs)
                {
                    rs.next();
                    firstInt = rs.getInt(1);
                    if (rs.wasNull())
                        firstInt = null;
                    if (rs.getMetaData().getColumnCount() >= 2)
                    {
                        secondInt = rs.getInt(2);
                        if (rs.wasNull())
                            secondInt = null;
                    }
                }

                if (null == _selectObjectIdIndex)
                {}
                else if (_selectObjectIdIndex == 2)
                {
                    _objectId = secondInt;
                }
                else if (_selectObjectIdIndex == 1)
                {
                    _objectId = firstInt;
                    firstInt = secondInt;
                }

                if (_selectRowId)
                    _rowId = firstInt;
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            return true;
        }


        public Integer getRowId()
        {
            return _rowId;
        }

        public Integer getObjectId()
        {
            return _objectId;
        }


        private void prepareParametersBeforeExecute() throws SQLException
        {
            for (Parameter p : _parameters)
            {
                if (!p._isSet)
                {
                    assert !p._constant;
                    if (!p._isNull)
                        p.setValue(null);
                }
            }
        }


        public void addBatch() throws SQLException
        {
            prepareParametersBeforeExecute();
            _stmt.addBatch();
        }


        @Override
        public void close() throws SQLException
        {
            try
            {
                _stmt.clearParameters();
            }
            catch (SQLException ignored)
            {
                // Don't blow up if the statement was already closed
            }
            _stmt.close();
            afterClose();
        }


//        public PreparedStatement getStatement()
//        {
//            return _stmt;
//        }

        public void clearParameters()
        {
            for (Parameter p : _parameters)
                if (!p._constant)
                    p._isSet = false;
        }


        public void put(String name, Object value) throws ValidationException
        {
            try
            {
                Parameter p = getParameter(name);
                if (null == p)
                    throw new IllegalArgumentException("parameter not found: " + name + ", available parameters are: " + _map.keySet());
                if (p._constant)
                    throw new IllegalStateException("Can't set constant parameter: " + name);
                p.setValue(value);
            }
            catch (SQLException sqlx)
            {
                SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(ExperimentService.get().getSchema().getScope().getDataSource());
                DataAccessException translated = translator.translate("Message", null, sqlx);
                if (translated instanceof DataIntegrityViolationException)
                {
                    throw new ValidationException(sqlx.getMessage() == null ? translated.getMessage() : sqlx.getMessage());
                }
                throw new RuntimeSQLException(sqlx);
            }
        }


        public void putAll(Map<String,Object> values)
        {
            try
            {
                for (Map.Entry<String,Object> e : values.entrySet())
                {
                    Parameter p = getParameter(e.getKey());
                    if (null != p)
                        p.setValue(e.getValue());
                }
            }
            catch (SQLException sqlx)
            {
                throw new RuntimeSQLException(sqlx);
            }
        }


        Runnable _onClose = null;
        boolean _runAfterTransaction = true;

        public void onClose(Runnable r)
        {
            if (null != _onClose)
                throw new IllegalStateException("only one onClose() callback supported");
            _onClose = r;
        }


        protected void afterClose()
        {
            if (null != _onClose)
            {
                if (_runAfterTransaction && _scope.isTransactionActive())
                    _scope.addCommitTask(_onClose, DbScope.CommitTaskOption.POSTCOMMIT);
                else
                    _onClose.run();
            }
            _onClose = null;
        }


        @Override
        protected void finalize() throws Throwable
        {
            super.finalize();
            assert null == _onClose;
            if (null != _onClose)
                _onClose.run();
        }


        String _debugSql;

        public String getDebugSql()
        {
            if (null != _debugSql)
                return _debugSql;
            if (_stmt instanceof StatementWrapper)
                return ((StatementWrapper)_stmt).getDebugSql();
            return null!=_sqlf ? _sqlf.toString() : null;
        }

        public void setDebugSql(String sql)
        {
            _debugSql = sql;
        }

        public DbScope getScope()
        {
            return _scope;
        }
    }

    public static class ParameterList extends ArrayList<Parameter> implements AutoCloseable
    {
        @Override
        public void close()
        {
            for (Parameter parameter : this)
            {
                parameter.close();
            }
            clear();
        }
    }
}
