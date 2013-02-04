/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HString;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * User: matthewb
 * Date: Feb 13, 2007
 * Time: 9:01:58 AM
 *
 * Parameter is bound to a particular parameter of a particular PreparedStatement
 *
 * TypedValue is useful for dragging along a sqlType with a raw value, usually this is not necessary as
 * type can be inferred.  However, this can be useful for NULL parameters and
 * for distiguishing unicode, non-unicode types
 *
 * NOTE: jdbc does not have separate Type values for varchar nvarchar
 * NOTE: does not do implicit conversion, just sets the parameter type
 */

public class Parameter
{
    private static Logger LOG = Logger.getLogger(Parameter.class);

    public static class TypedValue
    {
        Object _value;
        JdbcType _type;

        public TypedValue(Object value, JdbcType type)
        {
            this._value = value;
            this._type = type;
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

    
    public static TypedValue nullParameter(JdbcType sqlType)
    {
        return new TypedValue(null, sqlType);
    }


    private String _name;
    private @Nullable String _uri = null;  // for migration of ontology based code
    private final @Nullable JdbcType _type;

    // only allow setting once, do not clear
    private boolean _constant = false;
    
    private PreparedStatement _stmt;
    private int[] _indexes;


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
        _name = c.getName();
        _uri = c.getPropertyURI();
        _type = c.getJdbcType();
        _indexes = indexes;
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

    public void setValue(Object in, boolean constant) throws SQLException
    {
        setValue(in);
        _constant = constant;
    }

    public void setValue(@Nullable Object in) throws SQLException
    {
        if (_constant)
            throw new IllegalStateException("Can't set constant parameter");

        JdbcType type = _type;
        if (null == type)
        {
            if (in instanceof TypedValue)
                type = ((TypedValue)in)._type;
            else if (in instanceof StringExpression)
                type = JdbcType.VARCHAR;
        }

        Object value = getValueToBind(in, type);

        try
        {
            if (null == value)
            {
                setNull(type);
                return;
            }

            if (value instanceof AttachmentFile)
            {
                if (_indexes.length > 1)
                    throw new IllegalArgumentException("AttachmentFile can only be bound to a single parameter");

                try
                {
                    InputStream is = ((AttachmentFile) value).openInputStream();
                    long len = ((AttachmentFile) value).getSize();

                    if (len > Integer.MAX_VALUE)
                        throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
                    _stmt.setBinaryStream(_indexes[0], is, (int)len);
                    return;
                }
                catch (Exception x)
                {
                    SQLException sqlx = new SQLException();
                    sqlx.initCause(x);
                    throw sqlx;
                }
            }

            setObject(type, value);
        }
        catch (SQLException e)
        {
            LOG.error("Exception converting \"" + value + "\" to type " + _type);
            throw e;
        }
    }

    private void setNull(JdbcType type) throws SQLException
    {
        for (int index : _indexes)
            _stmt.setNull(index, null==type ? JdbcType.VARCHAR.sqlType : type.sqlType);
    }

    private void setObject(JdbcType type, Object value) throws SQLException
    {
        if (null == type)
            for (int index : _indexes)
                _stmt.setObject(index, value);
        else
            for (int index : _indexes)
                _stmt.setObject(index, value, type.sqlType);
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

        if (value instanceof TypedValue)
            value = ((TypedValue)value)._value;

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
        else if (value instanceof HString)
            return ((HString)value).getSource();
        else if (value.getClass() == java.lang.Character.class || value instanceof CharSequence)
            return value.toString();
        else if (value instanceof StringExpression)
            return ((StringExpression)value).getSource();
        else if (value.getClass() == Container.class)
            return ((Container) value).getId();
        else if (value instanceof Enum && type != null && type.isNumeric())
            return ((Enum)value).ordinal();
        else if (value instanceof Enum)
            return ((Enum)value).name();
        else if (value instanceof UserPrincipal)
            return ((UserPrincipal)value).getUserId();
        else if (value instanceof Role)
            return ((Role)value).getUniqueName();
        else if (value instanceof GUID)
            return value.toString();
        else if (value instanceof Class)
            return (((Class) value).getName());

        return value;
    }


    public String toString()
    {
        return "[" + (null==_indexes?"":Ints.join(",", _indexes)) + (null==_name?"":":"+_name) + "]";
    }


    public static class ParameterMap
    {
        PreparedStatement _stmt;
        boolean _selectRowId = false;
        Integer _selectObjectIdIndex = null;
        Integer _rowId;
        Integer _objectId;
        CaseInsensitiveHashMap<Parameter> _map;
        DbScope _scope;
        SqlDialect _dialect;

        public ParameterMap(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters)
        {
            this(scope, stmt, parameters, null);
        }

        public ParameterMap(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
        {
            init(scope, stmt, parameters, remap);
        }
        
        /**
         *  sql bound to constants or Parameters, compute the index array for each named Parameter
         */
        public ParameterMap(DbScope scope, Connection conn, SQLFragment sql, Map<String, String> remap) throws SQLException
        {
            PreparedStatement stmt;
            if (sql.getSQL().startsWith("{call"))
                stmt = conn.prepareCall(sql.getSQL());
            else
                stmt= conn.prepareStatement(sql.getSQL());

            IdentityHashMap<Parameter, IntegerArray> paramMap = new IdentityHashMap<Parameter,IntegerArray>();
            List<Object> paramList = sql.getParams();
            List<Parameter> parameters = new ArrayList<Parameter>(paramList.size());

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
            _map = new CaseInsensitiveHashMap<Parameter>(parameters.size() * 2);
            for (Parameter p : parameters)
            {
                if (null == p._name)
                    throw new IllegalStateException();
                p._stmt = stmt;
                String name = p._name;
                if (null != remap && remap.containsKey(name))
                    name = remap.get(name);
                if (_map.containsKey(name))
                    throw new IllegalArgumentException("duplicate parameter name: " + name);
                _map.put(name, p);
                if (null != p._uri)
                {
                    String uri = p._uri;
                    if (null != remap && remap.containsKey(uri))
                        uri = remap.get(uri);
                    if (_map.containsKey(uri))
                        throw new IllegalArgumentException("duplicate property uri: " + uri);
                    _map.put(uri, p);
                }
            }
            _stmt = stmt;
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
            return _map.get(name);
        }
        

        public void executeBatch() throws SQLException
        {
            _objectId = null;
            _rowId = null;
            _stmt.executeBatch();
        }


        public boolean execute() throws SQLException
        {
            ResultSet rs = null;
            _rowId = null;
            _objectId = null;

            try
            {
                if (_selectRowId || _selectObjectIdIndex != null)
                    rs = _dialect.executeInsertWithResults(_stmt);
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


        public void addBatch() throws SQLException
        {
            _stmt.addBatch();
        }


        public void close() throws SQLException
        {
            _stmt.close();
            afterClose();
        }


//        public PreparedStatement getStatement()
//        {
//            return _stmt;
//        }

        public void clearParameters() throws SQLException
        {
            _stmt.clearParameters();
            for (Parameter p : _map.values())
                if (!p._constant)
                    p.setValue(null);
        }


        public void put(String name, Object value) throws ValidationException
        {
            try
            {
                Parameter p = _map.get(name);
                if (null == p)
                    throw new IllegalArgumentException("parameter not found: " + name);
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
                    Parameter p = _map.get(e.getKey());
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
                    _scope.addCommitTask(_onClose);
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
    }
}
