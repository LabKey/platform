/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.exp.Lsid;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.UnexpectedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
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
    static Logger LOG = LogManager.getLogger(Parameter.class);

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


    String _name;
    @Nullable String _uri = null;       // for migration of ontology based code
    final @Nullable JdbcType _type;
    int _scale;
    int _precision;
    boolean setFileAsName = false;

    // only allow setting once, do not clear
    boolean _constant = false;

    PreparedStatement _stmt;
    private AutoCloseable _autoCloseable;
    int[] _indexes;

    // used to to optimize calls to Statement.set*()
    boolean _isSet = false;
    boolean _isNull = false;


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
        _scale = c.getScale();
        _precision = c.getPrecision();
        _indexes = indexes;
        // CONSIDER: this seems pretty low-level for this check (see also DefaultQueryUpdateService.convertTypes())
        setFileAsName = (c.getInputType().equalsIgnoreCase("file") && _type == JdbcType.VARCHAR);
    }


    public Parameter copy(PreparedStatement stmt)
    {
        Parameter copy = new Parameter(_name, _uri, _indexes, _type);
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

    public int getScale()
    {
        return _scale;
    }

    public int getPrecision()
    {
        return _precision;
    }


    public void setValue(@Nullable Object in)
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
            throw new RuntimeSQLException(e);
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
            _stmt.setNull(index, (null == type ? JdbcType.VARCHAR : type).sqlType);
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
                _stmt.setObject(index, value, type.sqlType == Types.TINYINT ? Types.SMALLINT : type.sqlType);
        _isSet = true;
        _isNull = (value == null);
    }

    
    // TODO: Switching to BeanUtils 1.8 (which has two-way converters) should let us clean this up significantly by
    // delegating most of the binding to our converters
    public static Object getValueToBind(@Nullable Object value, @Nullable JdbcType type)
    {
        if (value instanceof Callable)
        {
            try
            {
                value = ((Callable)value).call();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
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
        else if (value instanceof Lsid || value instanceof JSONObject || value instanceof File)
            return value.toString();

        return value;
    }



    public String toString()
    {
        return "[" + (null==_indexes?"":Ints.join(",", _indexes)) + (null==_name?"":":"+_name) + "]";
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
