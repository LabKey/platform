/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.HString;
import org.labkey.api.util.StringExpression;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.GregorianCalendar;

/**
 * User: matthewb
 * Date: Feb 13, 2007
 * Time: 9:01:58 AM
 *
 * parameter bound to a specific sql type, usually this is not necessary as
 * type can be inferred.  However, this can be useful for NULL parameters and
 * for distiguishing unicode, non-unicode types
 *
 * NOTE: jdbc does not have separate Type values for varchar nvarchar
 * NOTE: does not do implicit conversion, just sets the parameter type
 */

public class Parameter
{
    private final int _sqlType;
    private final Object _value;

    public Parameter(Object value)
    {
        this(value, Types.JAVA_OBJECT);
    }

    public Parameter(Object value, int sqlType)
    {
        // Use AttachmentFile instead
        assert !(value instanceof File || value instanceof MultipartFile);

        _value = value;
        if ((value == null || value instanceof StringExpression) && sqlType == Types.JAVA_OBJECT)
        {
            sqlType = Types.VARCHAR;
        }
        _sqlType = sqlType;
    }


    /**
     * For the case of SQL Server VARCHAR (as opposed to NVARCHAR) useUnicode=false
     * will convert the parameter (NVARCHAR parameter -> VARCHAR) so the database won't do the reverse
     * conversion (VARCHAR column -> NVARCHAR).
     *
     * @param value
     * @param useUnicode
     */
    public Parameter(Object value, boolean useUnicode, SqlDialect dialect)
    {
        if (!useUnicode && dialect.isSqlServer())
        {
            try
            {
                _value = String.valueOf(value).getBytes("ISO-8859-1");
                _sqlType = Types.VARBINARY;
            }
            catch (UnsupportedEncodingException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            _value = value;
            _sqlType = Types.VARCHAR;
        }
    }

    public static Parameter nullParameter(int sqlType)
    {
        return new Parameter(null, sqlType);
    }

    public void bind(PreparedStatement stmt, int index) throws SQLException
    {
        Object value = getValueToBind();

        if (null == value)
        {
            stmt.setNull(index, _sqlType);
            return;
        }

        if (value instanceof AttachmentFile)
        {
            try
            {
                InputStream is = ((AttachmentFile) value).openInputStream();
                long len = ((AttachmentFile) value).getSize();

                if (len > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
                stmt.setBinaryStream(index, is, (int)len);
                return;
            }
            catch (Exception x)
            {
                SQLException sqlx = new SQLException();
                sqlx.initCause(x);
                throw sqlx;
            }
        }

        if (_sqlType == Types.JAVA_OBJECT)
            stmt.setObject(index, value);
        else
            stmt.setObject(index, value, _sqlType);
    }

    public static void bindObject(PreparedStatement stmt, int index, Object value) throws SQLException
    {
        Parameter param;
        if (value instanceof Parameter)
            param = (Parameter)value;
        else
            param = new Parameter(value, Types.JAVA_OBJECT);

        param.bind(stmt, index);
    }

    public Object getValueToBind()
    {
        if (_value == null)
        {
            return null;
        }

        if (_value instanceof AttachmentFile)
        {
            return _value;
        }

        if (_value instanceof java.util.Date)
        {
            if (!(_value instanceof java.sql.Date) && !(_value instanceof java.sql.Time) && !(_value instanceof java.sql.Timestamp))
                return new java.sql.Timestamp(((java.util.Date) _value).getTime());
        }
        else if (_value instanceof GregorianCalendar)
            return new java.sql.Timestamp(((java.util.GregorianCalendar) _value).getTimeInMillis());
        else if (_value.getClass() == java.lang.Character.class || _value instanceof CharSequence)
        {
            if (_value instanceof HString)
                return ((HString)_value).getSource();
            else
                return _value.toString();
        }
        else if (_value instanceof StringExpression)
            return ((StringExpression)_value).getSource();
        else if (_value.getClass() == Container.class)
            return ((Container) _value).getId();
        else if (_value instanceof Enum)
            return ((Enum)_value).name();
        else if (_value instanceof Role)
            return ((Role)_value).getUniqueName();

        return _value;
    }

    public String toString()
    {
        return String.valueOf(_value);
    }
}
