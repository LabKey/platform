/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.util.DateUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * ENUM version of java.sql.Types
 */

public enum JdbcType
{
    BIGINT(Types.BIGINT, Long.class, Long.TYPE)
    {
        @Override
        protected Object _fromNumber(Number n)
        {
            return n.longValue();
        }

        @Override
        protected Object _fromString(String s)
        {
            // Be tolerant of trailing decimal zeros like "39.0", which Long.parseLong() is not
            return new BigDecimal(s).longValueExact();
        }
    },

    BINARY(Types.BINARY, ByteBuffer.class),

    // do we need separate BIT type?
    BOOLEAN(Types.BOOLEAN, Boolean.class, Boolean.TYPE)
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.BIT);
        }

        @Override
        protected Object _fromNumber(Number n)
        {
            return _toBoolean(n);
        }
    },

    CHAR(Types.CHAR, String.class)
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.NCHAR);
        }
    },

    DECIMAL(Types.DECIMAL, BigDecimal.class, null, "numberfield")
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.NUMERIC);
        }

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

    DOUBLE(Types.DOUBLE, Double.class, Double.TYPE, "numberfield")
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.FLOAT);
        }

        @Override
        protected Object _fromNumber(Number n)
        {
            return n.doubleValue();
        }

        @Override
        protected Object _fromString(String s)
        {
            return Double.valueOf(s);
        }
    },

    INTEGER(Types.INTEGER, Integer.class, Integer.TYPE, "numberfield")
    {
        @Override
        protected Object _fromNumber(Number n)
        {
            return _toInt(n);
        }

        @Override
        protected Object _fromString(String s)
        {
            // Be tolerant of trailing decimal zeros like "39.0", which Integer.parseInt() is not
            return new BigDecimal(s).intValueExact();
        }
    },

    LONGVARBINARY(Types.LONGVARBINARY, String.class)
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.BLOB);
        }
    },

    LONGVARCHAR(Types.LONGVARCHAR, String.class)
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.CLOB);
            sqlTypes.add(Types.LONGNVARCHAR);
        }

        @Override
        public Object convert(Object o) throws ConversionException
        {
            return VARCHAR.convert(o);
        }
    },

    REAL(Types.REAL, Float.class, Float.TYPE, "numberfield")
    {
        @Override
        protected Object _fromNumber(Number n)
        {
            return n.floatValue();
        }

        @Override
        protected Object _fromString(String s)
        {
            return Float.valueOf(s);
        }
    },

    SMALLINT(Types.SMALLINT, Short.class, Short.TYPE, "numberfield")
    {
        @Override
        protected Object _fromNumber(Number n)
        {
            return _toShort(n);
        }

        @Override
        protected Object _fromString(String s)
        {
            return Short.valueOf(s);
        }
    },

    DATE(Types.DATE, java.sql.Date.class, null, "datefield")
    {
        @Override
        protected Object _fromDate(Date d)
        {
            // presumably we would not be called if d instanceof Timestamp
            assert !(d instanceof java.sql.Date);
            return new java.sql.Date(d.getTime());
        }
    },

    TIME(Types.TIME, java.sql.Time.class, null, "timefield")
    {
        @Override
        protected Object _fromDate(Date d)
        {
            assert !(d instanceof Time);
            return new Time(d.getTime());
        }

        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            // For Redshift, see Issue 42311
            sqlTypes.add(Types.TIME_WITH_TIMEZONE);
        }
    },

    TIMESTAMP(Types.TIMESTAMP, java.sql.Timestamp.class, null, "datefield")
    {
        @Override
        protected Object _fromDate(Date d)
        {
            // presumably we would not be called if d instanceof Timestamp
            assert !(d instanceof Timestamp);
            return new Timestamp(d.getTime());
        }

        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            // For Redshift, see Issue 42311
            sqlTypes.add(Types.TIMESTAMP_WITH_TIMEZONE);
        }
    },

    TINYINT(Types.TINYINT, Short.class, Short.TYPE, "numberfield")
    {
        @Override
        protected Object _fromNumber(Number n)
        {
            return _toShort(n);
        }

        @Override
        protected Object _fromString(String s)
        {
            return Short.valueOf(s);
        }
    },

    VARBINARY(Types.VARBINARY, ByteBuffer.class),

    VARCHAR(Types.VARCHAR, String.class)
    {
        @Override
        protected void addSqlTypes(Collection<Integer> sqlTypes)
        {
            sqlTypes.add(Types.NVARCHAR);
        }

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

    GUID(Types.VARCHAR, String.class)
    {
        @Override
        // Types.VARCHAR should map to VARCHAR, not GUID
        protected Collection<Integer> getSqlTypes()
        {
            return Collections.emptySet();
        }
    },

    NULL(Types.NULL, Object.class),

    OTHER(Types.OTHER, Object.class);


    public final int sqlType;
    public final Class cls;
    public final String xtype;
    public final String json;

    private final Class typeCls;
    private final Converter converter;


    JdbcType(int type, @NotNull Class cls)
    {
        this(type, cls, null);
    }

    JdbcType(int type, @NotNull Class cls, @Nullable Class typeCls)
    {
        this(type, cls, typeCls, "textfield");
    }

    JdbcType(int type, @NotNull Class cls, @Nullable Class typeCls, String xtype)
    {
        // make sure ConvertHelper is initialized
        ConvertHelper.getPropertyEditorRegistrar();

        this.sqlType = type;
        this.cls = cls;
        this.typeCls = typeCls;
        this.xtype = xtype;
        this.json = DisplayColumn.getJsonTypeName(cls);
        this.converter = ConvertUtils.lookup(cls);
    }

    private static final HashMap<Class, JdbcType> classMap = new HashMap<>();
    private static final HashMap<Integer, JdbcType> sqlTypeMap = new HashMap<>();

    static
    {
        for (JdbcType t : values())
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

        for (JdbcType t : values())
        {
            for (Integer sqlType : t.getSqlTypes())
            {
                JdbcType prev = sqlTypeMap.put(sqlType, t);

                if (null != prev)
                    throw new IllegalStateException(t + " and " + prev + " have registered the same SqlType!");
            }
        }
    }


    // By default, valueOf() maps each JdbcType constant's sql.Types int to that constant
    protected Collection<Integer> getSqlTypes()
    {
        Collection<Integer> ret = new LinkedList<>();
        ret.add(sqlType);
        addSqlTypes(ret);

        return ret;
    }

    // JdbcType constant can map additional sql.Types ints to itself (see valueOf()) by overriding this method
    protected void addSqlTypes(Collection<Integer> sqlTypes)
    {
    }

    public static JdbcType valueOf(int type)
    {
        JdbcType jt = sqlTypeMap.get(type);

        return null != jt ? jt : OTHER;
    }

    public static JdbcType valueOf(Class cls)
    {
        JdbcType t = classMap.get(cls);
        if (null != t)
            return t;
        if (Enum.class.isAssignableFrom(cls))
            return VARCHAR;
        if (java.util.Date.class.isAssignableFrom(cls))
            return TIMESTAMP;
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

    public boolean isDecimal()
    {
        return isNumeric() &&
                this.getSqlTypes().contains(Types.DECIMAL) ||
                this.getSqlTypes().contains(Types.NUMERIC);
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

    // Return Object class
    public Class getJavaClass()
    {
        return cls;
    }
    
    // Return Object class or TYPE
    public Class getJavaClass(boolean isNullable)
    {
        return isNullable || null == typeCls ? cls : typeCls;
    }

    public Object convert(Object o) throws ConversionException
    {
        // Unwrap first
        if (o instanceof MvFieldWrapper)
        {
            o = ((MvFieldWrapper) o).getValue();
        }

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
        catch (NumberFormatException | ArithmeticException x)
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

            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException ignored){}
            try {BOOLEAN.convert(2.3); fail();} catch (ConversionException ignored){}
            try {INTEGER.convert(2.3); fail();} catch (ConversionException ignored){}
            try {INTEGER.convert("barney"); fail();} catch (ConversionException ignored){}
            try {BIGINT.convert("fred"); fail();} catch (ConversionException ignored){}
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
        public void valueOfClass()
        {
            assertEquals(VARCHAR, valueOf(String.class));
            assertEquals(INTEGER, valueOf(Integer.class));
            assertEquals(DOUBLE, valueOf(Double.class));
            assertEquals(TIMESTAMP, valueOf(java.util.Date.class));
            assertEquals(TIMESTAMP, valueOf(java.sql.Timestamp.class));
            assertEquals(BIGINT, valueOf(Long.class));

            assertEquals(BOOLEAN, valueOf(boolean.class));
            assertEquals(TINYINT, valueOf(byte.class));
            assertEquals(INTEGER, valueOf(char.class));
            assertEquals(SMALLINT, valueOf(short.class));
            assertEquals(INTEGER, valueOf(int.class));
            assertEquals(BIGINT, valueOf(long.class));
            assertEquals(REAL, valueOf(float.class));
            assertEquals(DOUBLE, valueOf(double.class));
        }

        @Test
        public void valueOfTypeInt()
        {
            assertEquals(BOOLEAN, valueOf(Types.BIT));
            assertEquals(BOOLEAN, valueOf(Types.BOOLEAN));
            assertEquals(TINYINT, valueOf(Types.TINYINT));
            assertEquals(SMALLINT, valueOf(Types.SMALLINT));
            assertEquals(INTEGER, valueOf(Types.INTEGER));
            assertEquals(BIGINT, valueOf(Types.BIGINT));
            assertEquals(DOUBLE, valueOf(Types.FLOAT));
            assertEquals(REAL, valueOf(Types.REAL));
            assertEquals(DOUBLE, valueOf(Types.DOUBLE));
            assertEquals(DECIMAL, valueOf(Types.NUMERIC));
            assertEquals(DECIMAL, valueOf(Types.DECIMAL));
            assertEquals(CHAR, valueOf(Types.NCHAR));
            assertEquals(CHAR, valueOf(Types.CHAR));
            assertEquals(VARCHAR, valueOf(Types.NVARCHAR));
            assertEquals(VARCHAR, valueOf(Types.VARCHAR));
            assertEquals(LONGVARCHAR, valueOf(Types.CLOB));
            assertEquals(LONGVARCHAR, valueOf(Types.LONGNVARCHAR));
            assertEquals(LONGVARCHAR, valueOf(Types.LONGVARCHAR));
            assertEquals(DATE, valueOf(Types.DATE));
            assertEquals(TIME, valueOf(Types.TIME));
            assertEquals(TIMESTAMP, valueOf(Types.TIMESTAMP));
            assertEquals(BINARY, valueOf(Types.BINARY));
            assertEquals(VARBINARY, valueOf(Types.VARBINARY));
            assertEquals(LONGVARBINARY, valueOf(Types.BLOB));
            assertEquals(LONGVARBINARY, valueOf(Types.LONGVARBINARY));

            assertEquals(OTHER, valueOf(Types.OTHER));
            assertEquals(OTHER, valueOf(Types.SQLXML));
            assertEquals(OTHER, valueOf(Types.DATALINK));
            assertEquals(OTHER, valueOf(Types.JAVA_OBJECT));
            assertEquals(OTHER, valueOf(Types.ROWID));
        }

        @Test
        public void sqlDateTime()
        {
            java.sql.Time ta = (java.sql.Time)JdbcType.TIME.convert("13:14:15");
            java.sql.Time tb = (java.sql.Time)JdbcType.TIME.convert(new Date(DateUtil.parseISODateTime("2001-02-03 13:14:15")));
            assertEquals(ta.toString(),tb.toString());

            try
            {
                // CONSIDER support this
                JdbcType.TIME.convert("2001-02-03 13:14:15");
                fail("expected ConversionException");
            }
            catch (ConversionException x)
            {
                /* pass */
            }

            java.sql.Date da = (java.sql.Date)JdbcType.DATE.convert("2001-02-03");
            java.sql.Date db = (java.sql.Date)JdbcType.DATE.convert(new Date(DateUtil.parseISODateTime("2001-02-03 13:14:15")));
            java.sql.Date dc = (java.sql.Date)JdbcType.DATE.convert("2001-02-03 13:14:15");
            assertEquals(da.toString(),db.toString());
            assertEquals(da.toString(),dc.toString());

            java.sql.Timestamp tsa = (java.sql.Timestamp)JdbcType.TIMESTAMP.convert("2001-02-03 13:14:15");
            java.sql.Timestamp tsb = (java.sql.Timestamp)JdbcType.TIMESTAMP.convert(new Date(DateUtil.parseISODateTime("2001-02-03 13:14:15")));
            assertEquals(tsa, tsb);
        }
    }
}
