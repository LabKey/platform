/*
 * Copyright (c) 2011-2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * ENUM version of java.sql.Types
 */

public enum JdbcType
{
    BIGINT(Types.BIGINT, Long.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return n.longValue();
                }
            },

    BINARY(Types.BINARY, ByteBuffer.class),

    // do we need separate BIT type?
    BOOLEAN(Types.BOOLEAN, Boolean.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return _toBoolean(n);
                }
            },

    CHAR(Types.CHAR, String.class),

    DECIMAL(Types.DECIMAL, BigDecimal.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return _toBigDecimal(n);
                }
            },

    DOUBLE(Types.DOUBLE, Double.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return n.doubleValue();
                }
            },

    INTEGER(Types.INTEGER, Integer.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return _toInt(n);
                }
            },

    LONGVARBINARY(Types.LONGVARBINARY, String.class),

    LONGVARCHAR(Types.LONGVARCHAR, String.class),

    REAL(Types.REAL, Float.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return n.floatValue();
                }
            },

    SMALLINT(Types.SMALLINT, Short.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return _toShort(n);
                }
            },

    DATE(Types.DATE, java.sql.Date.class),

    TIME(Types.TIME, java.sql.Time.class),

    TIMESTAMP(Types.TIMESTAMP, java.sql.Timestamp.class),

    TINYINT(Types.TINYINT, Short.class)
            {
                @Override
                protected Object _fromNumber(Number n)
                {
                    return _toShort(n);
                }
            },

    VARBINARY(Types.VARBINARY, ByteBuffer.class),

    VARCHAR(Types.VARCHAR, String.class),

    OTHER(Types.OTHER, String.class);



    public final int sqlType;
    public final Class cls;


    JdbcType(int type, Class cls)
    {
        this.sqlType = type;
        this.cls = cls;
    }


    public static JdbcType valueOf(int type)
    {
        switch (type)
        {
            case Types.BIT :
            case Types.BOOLEAN : return BOOLEAN;
            case Types.TINYINT : return TINYINT;
            case Types.SMALLINT : return SMALLINT;
            case Types.INTEGER : return INTEGER;
            case Types.BIGINT : return BIGINT;
            case Types.FLOAT : return DOUBLE;
            case Types.REAL : return REAL;
            case Types.DOUBLE : return DOUBLE;
            case Types.NUMERIC : return DECIMAL;
            case Types.DECIMAL : return DECIMAL;
            case Types.NCHAR :
            case Types.CHAR : return CHAR;
            case Types.NVARCHAR:
            case Types.VARCHAR : return VARCHAR;
            case Types.CLOB :
            case Types.LONGNVARCHAR :
            case Types.LONGVARCHAR : return LONGVARCHAR;
            case Types.DATE : return DATE;
            case Types.TIME : return TIME;
            case Types.TIMESTAMP : return TIMESTAMP;
            case Types.BINARY : return BINARY;
            case Types.VARBINARY : return VARBINARY;
            case Types.BLOB :
            case Types.LONGVARBINARY : return LONGVARBINARY;
            default : return OTHER;
        }
    }

    
    public Object convert(Object o)
    {
        if (null == o)
            return null;

        if (cls == o.getClass())
            return o;

        if (o instanceof Number)
        {
            Object r = _fromNumber((Number)o);
            if (null != r)
                return r;
            // fall through
        }

        String s = o instanceof String ? (String)o : ConvertUtils.convert(o);
        if (cls == String.class)
            return s;
        return ConvertUtils.convert(s, cls);
    }


    protected Object _fromNumber(Number n)
    {
        return null; // fall through
    }


    private static Boolean _toBoolean(Number n)
    {
        if (n instanceof Integer)
        {
            if (0 == n.longValue())
                return Boolean.FALSE;
            if (1 == n.longValue())
                return Boolean.TRUE;
        }
        throw new ConversionException("Expected boolean value");
    }


    private static Integer _toInt(Number n)
    {
        if (n.doubleValue() != (double)n.intValue())
            throw new ConversionException("Expected integer value");
        return n.intValue();
    }


    private static Short _toShort(Number n)
    {
        if (n.doubleValue() != (double)n.shortValue())
            throw new ConversionException("Expected integer value");
        return n.shortValue();
    }


    private static BigDecimal _toBigDecimal(Number n)
    {
        if (n instanceof BigDecimal)
            return (BigDecimal)n;
        if (n instanceof Double || n instanceof Float)
            return new BigDecimal(n.doubleValue());
        if (n instanceof Integer || n instanceof Long)
            return new BigDecimal(n.longValue());
        return new BigDecimal(n.toString());
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testConvert()
        {
            assert BOOLEAN.convert(true) instanceof Boolean;
            assert BOOLEAN.convert(false) instanceof Boolean;
            assert BOOLEAN.convert("true") instanceof Boolean;
            assert BOOLEAN.convert("false") instanceof Boolean;
            assert BOOLEAN.convert(0) instanceof Boolean;
            assert BOOLEAN.convert(1) instanceof Boolean;

            assert BIGINT.convert(1) instanceof Long;
            assert INTEGER.convert(1) instanceof Integer;
            assert SMALLINT.convert(1) instanceof Short;
            assert TINYINT.convert(1) instanceof Short;
            assert REAL.convert(1) instanceof Float;
            assert DOUBLE.convert(1) instanceof Double;
            assert DECIMAL.convert(1) instanceof BigDecimal;
            assert DECIMAL.convert(1.23) instanceof BigDecimal;
            assert BIGINT.convert("1") instanceof Long;
            assert INTEGER.convert("1") instanceof Integer;
            assert SMALLINT.convert("1") instanceof Short;
            assert TINYINT.convert("1") instanceof Short;
            assert REAL.convert("1") instanceof Float;
            assert DOUBLE.convert("1") instanceof Double;
            assert DECIMAL.convert("1") instanceof BigDecimal;

            assert VARCHAR.convert("wilma") instanceof String;
            assert VARCHAR.convert(5) instanceof String;            // should this be an error?
            assert CHAR.convert(5) instanceof String;               // should this be an error?
            assert LONGVARCHAR.convert(5) instanceof String;        // should this be an error?

            assert TIMESTAMP.convert("2001-02-03") instanceof Timestamp;

            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException x){}
            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException x){}
            try {INTEGER.convert(2.3); fail();} catch (ConversionException x){}
            try {INTEGER.convert("barney"); fail();} catch (ConversionException x){}
            try {BIGINT.convert("fred"); fail();} catch (ConversionException x){}
        }
    }
}