/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.DateUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;

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

            @Override
            protected Object _fromString(String s)
            {
                return new Long(s);
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

    DECIMAL(Types.DECIMAL, BigDecimal.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return _toBigDecimal(n);
            }

            @Override
            protected Object _fromString(String s)
            {
                return new BigDecimal(s);
            }
        },

    DOUBLE(Types.DOUBLE, Double.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return n.doubleValue();
            }

            @Override
            protected Object _fromString(String s)
            {
                return new Double(s);
            }
        },

    INTEGER(Types.INTEGER, Integer.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return _toInt(n);
            }

            @Override
            protected Object _fromString(String s)
            {
                return new Integer(s);
            }
        },

    LONGVARBINARY(Types.LONGVARBINARY, String.class),

    LONGVARCHAR(Types.LONGVARCHAR, String.class)
        {
            @Override
            public Object convert(Object o) throws ConversionException
            {
                return VARCHAR.convert(o);
            }
        },

    REAL(Types.REAL, Float.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return n.floatValue();
            }

            @Override
            protected Object _fromString(String s)
            {
                return new Float(s);
            }
        },

    SMALLINT(Types.SMALLINT, Short.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return _toShort(n);
            }


            @Override
            protected Object _fromString(String s)
            {
                return new Short(s);
            }
        },

    DATE(Types.DATE, java.sql.Date.class, "datefield"),

    TIME(Types.TIME, java.sql.Time.class, "timefield"),

    TIMESTAMP(Types.TIMESTAMP, java.sql.Timestamp.class, "datefield")
        {
            @Override
            protected Object _fromDate(Date d)
            {
                // presumably we would be called if d instanceof Timestamp
                assert !(d instanceof Timestamp);
                return new Timestamp(d.getTime());
            }
        },

    TINYINT(Types.TINYINT, Short.class, "numberfield")
        {
            @Override
            protected Object _fromNumber(Number n)
            {
                return _toShort(n);
            }

            @Override
            protected Object _fromString(String s)
            {
                return new Short(s);
            }
        },

    VARBINARY(Types.VARBINARY, ByteBuffer.class),

    VARCHAR(Types.VARCHAR, String.class)
        {
            @Override
            public Object convert(Object o) throws ConversionException
            {
                String s = (String)super.convert(o);
                return null==s || s.isEmpty() ? null : s;
            }

            @Override
            protected Object _fromDate(Date d)
            {
                return DateUtil.toISO(d);   // don't shorten
            }
        },

    GUID(Types.VARCHAR, String.class),

    NULL(Types.NULL, Object.class),

    OTHER(Types.OTHER, Object.class);


    public final int sqlType;
    public final Class cls;
    public final String xtype;
    public final String json;
    private final Converter converter;


    JdbcType(int type, Class cls)
    {
        this(type,cls,"textfield");
    }

    JdbcType(int type, Class cls, String xtype)
    {
        // make sure ConvertHelper is initialized
        ConvertHelper.getPropertyEditorRegistrar();

        this.sqlType = type==Types.TINYINT ? Types.SMALLINT : type;
        this.cls = cls;
        this.xtype = xtype;
        this.json = DisplayColumn.getJsonTypeName(cls);
        this.converter = ConvertUtils.lookup(cls);
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


    private static final HashMap<Class, JdbcType> classMap = new HashMap<>();

    static
    {
        for (JdbcType t : JdbcType.values())
            classMap.put(t.cls, t);

        // primitives
        classMap.put( boolean.class, BOOLEAN );
        classMap.put( byte.class, TINYINT );
        classMap.put( byte[].class, BINARY );
        classMap.put( char.class, INTEGER );
        classMap.put( short.class, SMALLINT );
        classMap.put( int.class, INTEGER );
        classMap.put( long.class, BIGINT );
        classMap.put( float.class, REAL );
        classMap.put( double.class, DOUBLE );
        classMap.put( Boolean.class, BOOLEAN );
        classMap.put( Byte.class, TINYINT );
        classMap.put( Character.class, SMALLINT );
        classMap.put( Short.class, SMALLINT );
        classMap.put( Integer.class, INTEGER );
        classMap.put( Long.class, BIGINT );
        classMap.put( Float.class, REAL );
        classMap.put( Double.class, DOUBLE );

        classMap.put( String.class, VARCHAR);
        classMap.put( java.util.Date.class, TIMESTAMP);
        classMap.put( java.sql.Timestamp.class, TIMESTAMP);
    }


    public static JdbcType valueOf(Class cls)
    {
        JdbcType t = classMap.get(cls);
        if (null != t)
            return t;
        if (Enum.class.isAssignableFrom(cls))
            return JdbcType.VARCHAR;
        if (java.util.Date.class.isAssignableFrom(cls))
            return JdbcType.TIMESTAMP;
        return null;
    }


    public static JdbcType promote(@Nullable JdbcType a, @Nullable JdbcType b)
    {
        if (null == a)
            a = NULL;
        if (null == b)
            b = NULL;
        if (a == b)
            return a;
        if (a == NULL) return b;
        if (b == NULL) return a;
        if (a == OTHER || b == OTHER) return OTHER;
        if (a == LONGVARCHAR || b == LONGVARCHAR)
            return LONGVARCHAR;
        if (a == VARCHAR || b == VARCHAR)
            return VARCHAR;
        boolean aIsNumeric = a.isNumeric(), bIsNumeric = b.isNumeric();
        if (aIsNumeric && bIsNumeric)
        {
            if (a == DOUBLE || a == REAL || b == DOUBLE || b == REAL)
                return DOUBLE;
            if (a == DECIMAL || b == DECIMAL)
                return DECIMAL;
            if (a == BIGINT || b == BIGINT)
                return BIGINT;
            return INTEGER;
        }
        if (a.isDateOrTime() && b.isDateOrTime())
        {
            return TIMESTAMP;
        }
        return OTHER;
    }


    public boolean isText()
    {
        return this.cls == String.class;
    }


    public boolean isNumeric()
    {
        return Number.class.isAssignableFrom(this.cls);
    }


    public boolean isInteger()
    {
        return isNumeric() &&
                this.cls == Byte.class ||
                this.cls == Short.class ||
                this.cls == Long.class ||
                this.cls == Integer.class ||
                this.cls == BigInteger.class;
    }

    public boolean isReal()
    {
        return isNumeric() && !isInteger();
    }

    public boolean isDateOrTime()
    {
        return java.util.Date.class.isAssignableFrom(this.cls);
    }

    public Class getJavaClass()
    {
        return cls;
    }
    
    public Object convert(Object o) throws ConversionException
    {
        if (null == o)
            return null;

        if (cls.isAssignableFrom(o.getClass()))
            return o;

        if (o instanceof Number)
        {
            Object r = _fromNumber((Number)o);
            if (null != r)
                return r;
        }

        if (o instanceof Date)
        {
            Object r = _fromDate((Date)o);
            if (null != r)
                return r;
        }

        String s = o instanceof String ? (String)o : ConvertUtils.convert(o);
        if (cls == String.class)
            return s;
        if (StringUtils.isEmpty(s))
            return null;

        try
        {
            Object r = _fromString(s);
            if (null != r)
                return r;
        }
        catch (NumberFormatException x)
        {
            throw new ConversionException("Expected decimal value", x);
        }

        if (converter == null)
        {
            throw new ConversionException("Unable to find converter for data class " + this.cls + ", unable to convert value: " + s);
        }

        // CONSIDER: convert may return default values instead of ConversionException
        return converter.convert(cls, s);
    }

    public static Object add(@NotNull Object obj1, @NotNull Object obj2, JdbcType type)
    {
        switch (type)
        {
            case BIGINT:
                return ConvertHelper.convert(obj1, Long.class) + ConvertHelper.convert(obj2, Long.class);
            case DECIMAL:
                return ConvertHelper.convert(obj1, BigDecimal.class).add(ConvertHelper.convert(obj2, BigDecimal.class));
            case DOUBLE:
                return ConvertHelper.convert(obj1, Double.class) + ConvertHelper.convert(obj2, Double.class);
            case INTEGER:
                return ConvertHelper.convert(obj1, Integer.class) + ConvertHelper.convert(obj2, Integer.class);
            case REAL:
                return ConvertHelper.convert(obj1, Float.class) + ConvertHelper.convert(obj2, Float.class);
            case SMALLINT:
                return ConvertHelper.convert(obj1, Short.class) + ConvertHelper.convert(obj2, Short.class);
            case TINYINT:
                return ConvertHelper.convert(obj1, Short.class) + ConvertHelper.convert(obj2, Short.class);
            default:
                throw new IllegalStateException("Cannot add non-numeric objects.");
        }
    }

    protected Object _fromNumber(Number n)
    {
        return null;
    }

    protected Object _fromString(String s)
    {
        return null;
    }

    protected Object _fromDate(Date d)
    {
        return null;
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
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert(true).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert(false).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert("true").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert("false").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert(0).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Boolean.class, BOOLEAN.convert(1).getClass());

            assertEquals("JdbcType.convert produced wrong type.", Long.class, BIGINT.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Integer.class, INTEGER.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Short.class, SMALLINT.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Short.class, TINYINT.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Float.class, REAL.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Double.class, DOUBLE.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", BigDecimal.class, DECIMAL.convert(1).getClass());
            assertEquals("JdbcType.convert produced wrong type.", BigDecimal.class, DECIMAL.convert(1.23).getClass());
            assertEquals("JdbcType.convert produced wrong type.", Long.class, BIGINT.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Integer.class, INTEGER.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Short.class, SMALLINT.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Short.class, TINYINT.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Float.class, REAL.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", Double.class, DOUBLE.convert("1").getClass());
            assertEquals("JdbcType.convert produced wrong type.", BigDecimal.class, DECIMAL.convert("1").getClass());

            assertEquals("JdbcType.convert produced wrong type.", String.class, VARCHAR.convert("wilma").getClass());
            assertEquals("JdbcType.convert produced wrong type.", String.class, VARCHAR.convert(5).getClass());            // should this be an error?
            assertEquals("JdbcType.convert produced wrong type.", String.class, CHAR.convert(5).getClass());               // should this be an error?
            assertEquals("JdbcType.convert produced wrong type.", String.class, LONGVARCHAR.convert(5).getClass());        // should this be an error?

            assertEquals("JdbcType.convert produced wrong type.", Timestamp.class, TIMESTAMP.convert("2001-02-03").getClass());
            assertEquals("JdbcType.convert produced wrong type.", java.sql.Date.class, DATE.convert("2001-02-03").getClass());

            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException x){}
            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException x){}
            try {INTEGER.convert(2.3); fail();} catch (ConversionException x){}
            try {INTEGER.convert("barney"); fail();} catch (ConversionException x){}
            try {BIGINT.convert("fred"); fail();} catch (ConversionException x){}
        }

        @Test
        public void testPromote()
        {
            assertEquals(promote(NULL, INTEGER), INTEGER);
            assertEquals(promote(NULL, VARCHAR), VARCHAR);
            assertEquals(promote(NULL, TIMESTAMP), TIMESTAMP);
            assertEquals(promote(SMALLINT, NULL), SMALLINT);
            assertEquals(promote(REAL, NULL), REAL);
            assertEquals(promote(LONGVARCHAR, NULL), LONGVARCHAR);

            assertEquals(promote(INTEGER, DOUBLE), DOUBLE);
            assertEquals(promote(INTEGER, SMALLINT), INTEGER);
            assertEquals(promote(INTEGER, DECIMAL), DECIMAL);
            assertEquals(promote(DECIMAL, DOUBLE), DOUBLE);

            assertEquals(promote(VARCHAR, DOUBLE), VARCHAR);
            assertEquals(promote(VARCHAR, SMALLINT), VARCHAR);
            assertEquals(promote(INTEGER, VARCHAR), VARCHAR);
            assertEquals(promote(DECIMAL, VARCHAR), VARCHAR);

            assertEquals(promote(TIME, DATE), TIMESTAMP);

            assertEquals(promote(BOOLEAN, DATE), OTHER);
            assertEquals(promote(VARBINARY, INTEGER), OTHER);
        }

        @Test
        public void valueOf()
        {
            assertEquals(JdbcType.VARCHAR, JdbcType.valueOf(String.class));
            assertEquals(JdbcType.INTEGER, JdbcType.valueOf(Integer.class));
            assertEquals(JdbcType.DOUBLE, JdbcType.valueOf(Double.class));
            assertEquals(JdbcType.TIMESTAMP, JdbcType.valueOf(java.util.Date.class));
            assertEquals(JdbcType.TIMESTAMP, JdbcType.valueOf(java.sql.Timestamp.class));
            assertEquals(JdbcType.BIGINT, JdbcType.valueOf(Long.class));

            assertEquals(JdbcType.BOOLEAN, JdbcType.valueOf(boolean.class));
            assertEquals(JdbcType.TINYINT, JdbcType.valueOf(byte.class));
            assertEquals(JdbcType.INTEGER, JdbcType.valueOf(char.class));
            assertEquals(JdbcType.SMALLINT, JdbcType.valueOf(short.class));
            assertEquals(JdbcType.INTEGER, JdbcType.valueOf(int.class));
            assertEquals(JdbcType.BIGINT, JdbcType.valueOf(long.class));
            assertEquals(JdbcType.REAL, JdbcType.valueOf(float.class));
            assertEquals(JdbcType.DOUBLE, JdbcType.valueOf(double.class));
        }
    }
}
