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

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.beanutils.converters.BigIntegerConverter;
import org.apache.commons.beanutils.converters.BooleanArrayConverter;
import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.beanutils.converters.ByteArrayConverter;
import org.apache.commons.beanutils.converters.ByteConverter;
import org.apache.commons.beanutils.converters.CharacterArrayConverter;
import org.apache.commons.beanutils.converters.CharacterConverter;
import org.apache.commons.beanutils.converters.ClassConverter;
import org.apache.commons.beanutils.converters.DoubleArrayConverter;
import org.apache.commons.beanutils.converters.FloatArrayConverter;
import org.apache.commons.beanutils.converters.FloatConverter;
import org.apache.commons.beanutils.converters.IntegerArrayConverter;
import org.apache.commons.beanutils.converters.LongArrayConverter;
import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.commons.beanutils.converters.ShortArrayConverter;
import org.apache.commons.beanutils.converters.ShortConverter;
import org.apache.commons.beanutils.converters.SqlTimestampConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.junit.Assert;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleConverter;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.TimeOnlyDate;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLRecordConverter;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import java.awt.*;
import java.beans.PropertyEditorSupport;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import org.junit.Test;


public class ConvertHelper implements PropertyEditorRegistrar
{
    private static final ConvertHelper _myInstance = new ConvertHelper();

    // just a list of converters we know about
    private final Set<Class> _converters = new ConcurrentHashSet<>();


    public static PropertyEditorRegistrar getPropertyEditorRegistrar()
    {
        return _myInstance;
    }


    private ConvertHelper()
    {
        BeanUtilsBean.setInstance(new BeanUtilsBean(new EnumAwareConvertUtilsBean(), new PropertyUtilsBean()));
        register();
    }

    public static class EnumAwareConvertUtilsBean extends ConvertUtilsBean
    {
        private final EnumConverter _enumConverter = new EnumConverter();

        @Override
        public Converter lookup(Class clazz)
        {
            Converter c = super.lookup(clazz);
            if (c != null)
                return c;

            if (clazz != null && clazz.isEnum())
                return _enumConverter;

            return null;
        }
    }


    // Replace default registered converters which return default values (e.g. 0) for errors.
    protected void register()
    {
        _register(new NullSafeConverter(new BigDecimalConverter()), BigDecimal.class);
        _register(new NullSafeConverter(new BigIntegerConverter()), BigInteger.class);
        _register(new NullSafeConverter(new BooleanConverter()), Boolean.class);
        _register(new BooleanConverter(), Boolean.TYPE);
        _register(new NullSafeConverter(new BooleanArrayConverter()), boolean[].class);
        _register(new NullSafeConverter(new ByteConverter()), Byte.class);
        _register(new ByteConverter(), Byte.TYPE);
        _register(new NullSafeConverter(new SQLByteArrayConverter()), byte[].class);
        _register(new NullSafeConverter(new CharacterArrayConverter()), char[].class);
        _register(new NullSafeConverter(new CharacterConverter()), Character.class);
        _register(new CharacterConverter(), Character.TYPE);
        _register(new NullSafeConverter(new ClassConverter()), Class.class);
        _register(new ColorConverter(), Color.class);
        _register(new ContainerConverter(), Container.class);
        _register(new GuidConverter(), GUID.class);
        _register(new InfDoubleConverter(), Double.TYPE);
        _register(new InfDoubleConverter(), Double.class);
        _register(new NullSafeConverter(new DoubleArrayConverter()), double[].class);
        _register(new NullSafeConverter(new FloatConverter()), Float.class);
        _register(new FloatConverter(), Float.TYPE);
        _register(new FloatArrayConverter(), float[].class);
        _register(new ReturnURLString.Converter(), ReturnURLString.class);
        _register(new NullSafeConverter(new IntegerArrayConverter()), int[].class);
        _register(new NullSafeConverter(new _IntegerConverter()), Integer.class);
        _register(new _IntegerConverter(), Integer.TYPE);
        _register(new NullSafeConverter(new LenientSqlDateConverter()), java.sql.Date.class);
        _register(new NullSafeConverter(new SqlTimestampConverter()), java.sql.Time.class);
        _register(new LenientTimestampConverter(), java.sql.Timestamp.class);
        _register(new LenientDateConverter(), java.util.Date.class);
        _register(new NullSafeConverter(new LongConverter()), Long.class);
        _register(new LongConverter(), Long.TYPE);
        _register(new NullSafeConverter(new LongArrayConverter()), long[].class);
        _register(new ReportIdentifierConverter(), ReportIdentifier.class);
        _register(new RoleConverter(), Role.class);
        _register(new NullSafeConverter(new ShortConverter()), Short.class);
        _register(new ShortConverter(), Short.TYPE);
        _register(new NullSafeConverter(new ShortArrayConverter()), short[].class);
        _register(new NullSafeConverter(new DateFriendlyStringConverter()), String.class);
        _register(new StringArrayConverter(), String[].class);
        _register(new URLHelper.Converter(), URLHelper.class);
        _register(new StringExpressionFactory.Converter(), StringExpression.class);
        _register(new LenientTimeOnlyConverter(), TimeOnlyDate.class);
        _register(new ShowRowsConverter(), ShowRows.class);
        _register(new UserConverter(), User.class);
        _register(new ExpDataFileConverter(), File.class);
        _register(new FacetingBehaviorTypeConverter(), FacetingBehaviorType.class);
        _register(new DefaultScaleConverter(), DefaultScaleType.class);
        _register(new SchemaKey.Converter(), SchemaKey.class);
        _register(new FieldKey.Converter(), FieldKey.class);
        _register(new JSONTypeConverter(), JSONObject.class);
        _register(new ShortURLRecordConverter(), ShortURLRecord.class);
        _register(new ColumnHeaderType.Converter(), ColumnHeaderType.class);
    }


    protected void _register(Converter conv, Class cl)
    {
        ConvertUtils.register(conv, cl);
        _converters.add(cl);
    }


    public void registerCustomEditors(PropertyEditorRegistry registry)
    {
        for (Class c : _converters)
        {
            registry.registerCustomEditor(c, new ConvertUtilsEditor(c));
        }
    }


    public static class NullSafeConverter implements Converter
    {
        Converter _converter;

        public NullSafeConverter(Converter converter)
        {
            _converter = converter;
        }

        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
            {
                // 2956 : AbstractConvertHelper shouldn't trim Strings
                //o = ((String) o).trim();
                if (((String) o).length() == 0)
                    return null;
            }

            return null == o || "".equals(o) ? null : _converter.convert(clss, o);
        }
    }


    /**
     * This converter converts dates with a time portion only.
     * A duration is a date without year, month, or day components (e.g., 2:01 or 2:01:32).
     */
    public static class LenientTimeOnlyConverter implements Converter
    {
        private static final String[] VALID_FORMATS = {"H:mm", "H:mm:ss"};
        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Time || o instanceof TimeOnlyDate)
                return o;

            Date duration = null;
            ParseException parseException = null;
            for (int i = 0; i < VALID_FORMATS.length && duration == null; i++)
            {
                try
                {
                    duration = DateUtil.parseDateTime(o.toString(), VALID_FORMATS[i]);
                }
                catch (ParseException e)
                {
                    parseException = e;
                }
            }
            if (duration == null)
                throw new ConversionException("Could not convert \"" + o.toString() + "\" to duration.", parseException);
            else
                return new TimeOnlyDate(duration.getTime());
        }
    }

    /**
     * This converter converts timestamps.
     * A timestamp is a date plus milliseconds.  (e.g., 6/1/2009 16:01.432)
     */
    public static class LenientTimestampConverter implements Converter
    {
        private LenientDateConverter _dateConverter = new LenientDateConverter();

        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
                o = StringUtils.trimToNull((String)o);

            if (null == o)
                return null;

            if (o instanceof Timestamp)
                return o;

            Date date;
            String signedDurationCandidate = StringUtils.trimToNull((String) o);
            if (DateUtil.isSignedDuration(signedDurationCandidate))
            {
                date = new Date(DateUtil.applySignedDuration(new Date().getTime(), signedDurationCandidate));
            }
            else
            {
                date = (Date) _dateConverter.convert(Date.class, o);
            }
            return null==date ? null : new Timestamp(date.getTime());
        }
    }

    /**
     * This format accepts dates in the form MM/dd/yy
     */
    public static class LenientDateConverter implements Converter
    {
        public Object convert(Class clss, Object o)
        {
            if (null == o || "".equals(o))
                return null;

            if (o instanceof java.util.Date)
                return o;

            return new Date(DateUtil.parseDateTime(o.toString()));
        }
    }


    /* Date-only sql type */
    public static class LenientSqlDateConverter implements Converter
    {
        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
                o = StringUtils.trimToNull((String)o);
            if (null == o)
                return null;

            if (o instanceof java.sql.Date)
                return o;

            if (o instanceof java.util.Date)
                return new java.sql.Date(((java.util.Date)o).getTime());

            return new java.sql.Date(DateUtil.parseDateTime(o.toString()));
        }
    }


    public static class DateFriendlyStringConverter implements Converter
    {
        private static Converter _stringConverter = new StringConverter();

        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
                return o;

            if (o instanceof Container)
                return ((Container)o).getId();

            if (o instanceof Date)
            {
                return DateUtil.toISO(((Date)o).getTime(), false);
            }

            if (o instanceof Clob)
            {
                try
                {
                    return convertClobToString((Clob)o);
                }
                catch (SQLException e)
                {
                    throw new ConversionException("Could not convert Clob to String", e);
                }
            }

            return _stringConverter.convert(String.class, o);
        }
    }


    // Note: When using getObject() on a SQL column of type Text or NText, the Microsoft SQL Server jdbc driver returns
    // a String, while the jTDS driver returns a Clob.  For consistency we map here.  Could map at lower level, but
    // don't want to preclude ever using Clob as Clob.
    public static String convertClobToString(Clob clob) throws SQLException
    {
        return clob.getSubString(1, (int) clob.length());
    }


    public static class ContainerConversionException extends ConversionException
    {
        public ContainerConversionException(String msg)
        {
            super(msg);
        }
    }


    public static class ContainerConverter implements Converter
    {
        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Container)
                return o;

            String str = o.toString().trim();
            if ("".equals(str))
                return null;

            Container c;

            if (str.startsWith("/"))
            {
                c = ContainerManager.getForPath(str);
            }
            else if (str.startsWith(Container.class.getName() + "@") && str.contains(" "))
            {
                // Sometimes we get called with a Container.toString() value since the Apache conversion code
                // doesn't appear to handle a no-op conversion on Containers - it calls toString() to force us to convert
                String id = str.substring(str.lastIndexOf(' ') + 1);
                c = ContainerManager.getForId(id);
            }
            else
            {
                c = ContainerManager.getForId(str);
            }

            if (null == c)
                throw new ContainerConversionException("Could not convert: " + str + " to container.  Container not found.");

            return c;
        }
    }


    public static class GuidConverter implements Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (null == value)
                return null;
            if (value instanceof GUID)
                return value;
            return new GUID(String.valueOf(value));
        }
    }


    public static class SQLByteArrayConverter implements Converter
    {
        private final Converter _converter;

        public SQLByteArrayConverter(Converter converter)
        {
            _converter = converter;
        }

        public SQLByteArrayConverter()
        {
            _converter = new ByteArrayConverter();
        }

        public Object convert(Class clazz, Object o)
        {
            if (clazz != byte[].class)
                return _converter.convert(clazz, o);
            if (!(o instanceof Blob))
                return _converter.convert(clazz, o);
            Blob blob = (Blob) o;
            try
            {
                long length = blob.length();
                if (length > Integer.MAX_VALUE)
                {
                    throw new ConversionException("Blob length too long:" + blob.length());
                }

                return blob.getBytes(1, (int) length);
            }
            catch (SQLException e)
            {
                throw new ConversionException("SQL error converting blob to bytes", e);
            }
        }
    }


    public static final class _IntegerConverter implements Converter
    {

        // ----------------------------------------------------------- Constructors


        /**
         * Create a {@link Converter} that will throw a {@link ConversionException}
         * if a conversion error occurs.
         */
        public _IntegerConverter()
        {

            this.defaultValue = null;
            this.useDefault = false;

        }


        /**
         * Create a {@link Converter} that will return the specified default value
         * if a conversion error occurs.
         *
         * @param defaultValue The default value to be returned
         */
        public _IntegerConverter(Object defaultValue)
        {

            this.defaultValue = defaultValue;
            this.useDefault = true;

        }

        // ----------------------------------------------------- Instance Variables


        /**
         * The default value specified to our Constructor, if any.
         */
        private Object defaultValue = null;


        /**
         * Should we return the default value on conversion errors?
         */
        private boolean useDefault = true;

        // --------------------------------------------------------- Public Methods


        /**
         * Convert the specified input object into an output object of the
         * specified type.
         *
         * @param type  Data type to which this value should be converted
         * @param value The input value to be converted
         * @throws ConversionException if conversion cannot be performed
         *                             successfully
         */
        public Object convert(Class type, Object value)
        {

            if (value == null)
            {
                if (useDefault)
                {
                    return (defaultValue);
                }
                else
                {
                    throw new ConversionException("No value specified");
                }
            }

            if (value instanceof Integer)
            {
                return value;
            }
            else if (value instanceof Number)
            {
                // NOTE: Inconsistent with String case which disallows non-zero fractional part
                return ((Number) value).intValue();
            }

            String s = value.toString();
            try
            {
                try
                {
                    return Integer.parseInt(s);
                }
                catch (NumberFormatException x)
                {
                    return (new BigDecimal(s)).intValueExact();
                }
            }
            catch (Exception e)
            {
                if (useDefault)
                {
                    return (defaultValue);
                }
                else
                {
                    throw new ConversionException("Could not convert '" + s + "' to an integer", e);
                }
            }
        }
    }

    /** Simple genericized wrapper around ConvertUtils.convert(). Also handles calling toString() on value, if non-null */
    public static <T> T convert(Object value, Class<T> cl)
    {
        if (value == null)
        {
            return null;
        }
        return (T)ConvertUtils.convert(value.toString(), cl);
    }

    public static class ConvertUtilsEditor extends PropertyEditorSupport
        {
        Class _class;

        ConvertUtilsEditor(Class c)
        {
            _class = c;
        }

        public void setAsText(String text) throws IllegalArgumentException
        {
            try
            {
            text = StringUtils.trimToNull(text);
            if (null == text)
                setValue(null);
            else
                setValue(ConvertUtils.convert(text, _class));
            }
            catch (ConversionException x)
            {
                throw new IllegalArgumentException(x.getMessage(), x);
            }
        }

        public String getAsText()
        {
            Object v = getValue();
            return v == null ? null : ConvertUtils.convert(getValue());
        }
    }


    public static class ColorConverter implements Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return Color.WHITE;
            if (value.getClass() == type)
                return value;
            String s = value.toString();
            return Color.decode(s);
        }
    }

    // see bug 5340 : Spring Data binding bizarreness. Crash when edit visit in study with exactly one dataset
    public static class StringArrayConverter implements Converter
    {
        private org.apache.commons.beanutils.converters.StringArrayConverter _nested =
                new org.apache.commons.beanutils.converters.StringArrayConverter();

        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof String[])
                return value;
            if (value instanceof String)
            {
                // If the value is wrapped with { and }, let the beanutils converter tokenize the values.
                // This let's us handle Issue 5340 while allowing multi-value strings to be parsed.
                String s = (String)value;
                if (s.startsWith("{") && s.endsWith("}"))
                    return _nested.convert(type, value);
            }

            // Otherwise, treat it as a single element string array.
            return new String[] {String.valueOf(value)};
        }
    }

    public static class UserConverter implements Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null || value.equals("null") || !type.equals(User.class))
                return null;
            else
            {
                return UserManager.getUser(NumberUtils.toInt(value.toString(), -1));
            }
        }
    }

    public static class FacetingBehaviorTypeConverter implements Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null || value.equals("null") || !type.equals(FacetingBehaviorType.class))
                return null;
            else
            {
                return FacetingBehaviorType.valueOf(value.toString());
            }
        }
    }

    public static class DefaultScaleConverter implements Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null || value.equals("null") || !type.equals(DefaultScaleType.class))
                return null;
            else
            {
                return DefaultScaleType.valueOf(value.toString());
            }
        }
    }

    public static class JSONTypeConverter implements Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null || value.equals("null"))
                return null;

            if (value.getClass() == type)
                return value;

            if (value instanceof Map)
                return new JSONObject((Map)value);
            if (value instanceof String)
                return new JSONObject((String)value);

            throw new ConversionException("Could not convert '" + value + "' to an JSONObject");
        }
    }


    /* Java 7 formats using infinity symbol instead of "Infinity", but doesn't parse it */
    public static class InfDoubleConverter implements Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value instanceof String && ((String)value).isEmpty())
                return null;
            if (value == null)
                return null;

            if (value instanceof Double)
            {
                return (value);
            }
            else if (value instanceof Number)
            {
                return new Double(((Number) value).doubleValue());
            }

            String s = value.toString();
            try
            {
                return new Double(s);
            }
            catch (NumberFormatException x)
            {
                if (s.equals("\u221E"))
                    return Double.POSITIVE_INFINITY;
                else if (s.equals("-\u221E"))
                    return Double.NEGATIVE_INFINITY;
                else if (s.equalsIgnoreCase("Inf"))
                    return Double.POSITIVE_INFINITY;
                else if (s.equalsIgnoreCase("-Inf"))
                    return Double.NEGATIVE_INFINITY;
                else if (s.equals("\uFFFD"))
                    return Double.NaN;
                throw new ConversionException(x);
            }
            catch (Exception e)
            {
                throw new ConversionException(e);
            }
        }
    }

    public static class EnumConverter implements Converter
    {
        @SuppressWarnings("unchecked")
        public Object convert(Class type, Object value)
        {
            if (!type.isEnum())
                throw new IllegalArgumentException();

            if (value == null)
                return null;

            try
            {
                return Enum.valueOf(type, value.toString());
            }
            catch (IllegalArgumentException e)
            {
                try
                {
                    int ordinal = Integer.parseInt(value.toString());
                    Object[] values = type.getEnumConstants();
                    if (ordinal >= 0 && ordinal <= values.length)
                    {
                        return values[ordinal];
                    }
                }
                // That's OK, not an ordinal value for the enum
                catch (NumberFormatException ignored) {}

                throw new ConversionException(e);
            }
        }


    }

    public static class TestCase extends Assert
    {
        @Test
        public void testConvertDate()
        {

            Object convertedDate = new LenientTimestampConverter().convert(Timestamp.class, "+1d");
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.DATE, 1);
            assertEquals("Wrong date", DateUtil.getDateOnly(cal.getTime()),
                    DateUtil.getDateOnly((Timestamp)convertedDate));

            convertedDate = new LenientTimestampConverter().convert(Timestamp.class, "-2m0d");
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -2);
            assertEquals("Wrong date", DateUtil.getDateOnly(cal.getTime()),
                     DateUtil.getDateOnly((Timestamp)convertedDate));

            convertedDate = new LenientTimestampConverter().convert(Timestamp.class, "Thu Jun 10 00:00:00 PDT 1999");
            cal.set(1999, Calendar.JUNE,10,0,0,0);
            assertEquals("Wrong date", DateUtil.getDateOnly(cal.getTime()),
                     DateUtil.getDateOnly((Timestamp)convertedDate));

        }

    }
}
