/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.beanutils.SuppressPropertiesBeanIntrospector;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.beanutils.converters.BigIntegerConverter;
import org.apache.commons.beanutils.converters.BooleanArrayConverter;
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
import org.apache.commons.beanutils.converters.ShortArrayConverter;
import org.apache.commons.beanutils.converters.ShortConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
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
import org.labkey.api.util.SimpleTime;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;


/**
 * Holder for {@link Converter} implementations that convert from strings to strongly typed objects for a wide variety of classes
 */
public class ConvertHelper implements PropertyEditorRegistrar
{
    private static final ConvertHelper _myInstance = new ConvertHelper();

    // just a list of converters we know about
    private final Set<Class<?>> _converters = new ConcurrentHashSet<>();


    public static PropertyEditorRegistrar getPropertyEditorRegistrar()
    {
        return _myInstance;
    }


    private ConvertHelper()
    {
        BeanUtilsBean bub = new BeanUtilsBean(new EnumAwareConvertUtilsBean(), new PropertyUtilsBean());
        bub.getPropertyUtils().addBeanIntrospector(SuppressPropertiesBeanIntrospector.SUPPRESS_CLASS);
        BeanUtilsBean.setInstance(bub);
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
        _register(new NullSafeConverter(new LenientTimeConverter()), java.sql.Time.class);
        _register(new LenientTimestampConverter(), java.sql.Timestamp.class);
        _register(new LenientDateConverter(), java.util.Date.class);
        _register(new NullSafeConverter(new _LongConverter()), Long.class);
        _register(new _LongConverter(), Long.TYPE);
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
        _register(new SimpleTimeConverter(), SimpleTime.class);
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


    protected void _register(Converter conv, Class<?> cl)
    {
        ConvertUtils.register(conv, cl);
        _converters.add(cl);
    }


    @Override
    public void registerCustomEditors(@NotNull PropertyEditorRegistry registry)
    {
        for (Class<?> c : _converters)
        {
            registry.registerCustomEditor(c, new ConvertUtilsEditor(c));
        }
    }


    public static class NullSafeConverter implements Converter
    {
        private final Converter _converter;

        public NullSafeConverter(Converter converter)
        {
            _converter = converter;
        }

        @Override
        public <T> T convert(Class<T> clss, Object o)
        {
            if (o instanceof String s)
            {
                // 2956 : AbstractConvertHelper shouldn't trim Strings
                //o = ((String) o).trim();
                if (s.isEmpty())
                    return null;
            }

            return null == o ? null : _converter.convert(clss, o);
        }
    }

    // Issue 34334: Add 't' and 'f' to BooleanConverter in order to support PostgreSQL array_to_string of booleans array.
    // For example, array_to_string(array_agg(array[true, false]), '|') ==> returns 't|f'
    public static class BooleanConverter implements Converter
    {
        private final org.apache.commons.beanutils.converters.BooleanConverter _nested = new org.apache.commons.beanutils.converters.BooleanConverter();

        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;

            if (value instanceof Boolean)
                return value;

            String str = value.toString().trim();
            if (str.equalsIgnoreCase("t"))
                return Boolean.TRUE;
            else if (str.equalsIgnoreCase("f"))
                return Boolean.FALSE;

            return _nested.convert(type, str);
        }
    }

    /**
     * This converter converts dates with a time portion only.
     * A duration is a date without year, month, or day components (e.g., 2:01 or 2:01:32).
     */
    public static class LenientTimeOnlyConverter implements Converter
    {

        @Override
        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Time || o instanceof TimeOnlyDate)
                return o;

            return new TimeOnlyDate(DateUtil.parseSimpleTime(o).getTime());
        }
    }

    // used by DataLoader to infer domain field types
    public static class SimpleTimeConverter implements Converter
    {
        @Override
        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Time || o instanceof TimeOnlyDate)
                return o;

            Time parsedTime = DateUtil.fromTimeString(o.toString(), true, true);
            // must throw error for DataLoader.inferColumnInfo to try a next classesToTest
            if (parsedTime == null)
                throw new ConversionException("Cannot convert " + o + " to Time");
            return parsedTime;
        }
    }

    public static class LenientTimeConverter implements Converter
    {
        @Override
        public Object convert(Class clss, Object o)
        {
            if (null == o || "".equals(o))
                return null;

            if (o instanceof java.sql.Time)
                return o;

            return DateUtil.fromTimeString(o.toString(), false);
        }
    }

    /**
     * This converter converts timestamps.
     * A timestamp is a date plus milliseconds.  (e.g., 6/1/2009 16:01.432)
     */
    public static class LenientTimestampConverter implements Converter
    {
        private final LenientDateConverter _dateConverter = new LenientDateConverter();

        @Override
        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
                o = StringUtils.trimToNull((String)o);

            if (null == o)
                return null;

            if (o instanceof Timestamp)
                return o;

            Date date;
            String signedDurationCandidate = StringUtils.trimToNull(o instanceof String s ? s : null);
            if (signedDurationCandidate != null && DateUtil.isSignedDuration(signedDurationCandidate))
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
     * This converter accepts dates in a variety of standard formats
     */
    public static class LenientDateConverter implements Converter
    {
        @Override
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
        @Override
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
        private static final Converter _stringConverter = new StringConverter();

        @Override
        public Object convert(Class clss, Object o)
        {
            if (o instanceof String)
                return o;

            if (o instanceof Container)
                return ((Container)o).getId();

            if (o instanceof Time)
            {
                return o.toString();
            }

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
        @Override
        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Container)
                return o;

            String str = o.toString().trim();
            if (str.isEmpty())
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

            if (c == null && !str.startsWith("/"))
            {
                // Try once more, as it might be a path without the leading slash. See issue 49470
                c = ContainerManager.getForPath(str);
            }

            if (null == c)
                throw new ContainerConversionException("Could not convert supplied value to a container. Container not found.");

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
            try
            {
                return new GUID(String.valueOf(value));
            }
            catch (Exception e)
            {
                throw new ConversionException("Could not convert '" + value + "' to a GUID", e);
            }
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

        @Override
        public Object convert(Class clazz, Object o)
        {
            if (clazz != byte[].class)
                return _converter.convert(clazz, o);
            if (!(o instanceof Blob blob))
                return _converter.convert(clazz, o);
            try
            {
                long length = blob.length();
                if (length > Integer.MAX_VALUE)
                {
                    throw new ConversionExceptionWithMessage("Blob length too long:" + blob.length());
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
        private final Object defaultValue;


        /**
         * Should we return the default value on conversion errors?
         */
        private final boolean useDefault;

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
        @Override
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
            else if (value instanceof Number n)
            {
                try
                {
                    return BigDecimal.valueOf(n.doubleValue()).intValueExact();
                }
                catch (Exception e)
                {
                    throw new ConversionException("Could not convert '" + value + "' to an integer", e);
                }
            }

            String s = value.toString().trim();
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
                    throw new ConversionException("Could not convert '" + value + "' to an integer", e);
                }
            }
        }
    }

    public static final class _LongConverter implements Converter
    {

        // ----------------------------------------------------------- Constructors
        /**
         * Create a {@link Converter} that will throw a {@link ConversionException}
         * if a conversion error occurs.
         */
        public _LongConverter()
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
        public _LongConverter(Object defaultValue)
        {
            this.defaultValue = defaultValue;
            this.useDefault = true;
        }

        // ----------------------------------------------------- Instance Variables
        /**
         * The default value specified to our Constructor, if any.
         */
        private final Object defaultValue;

        /**
         * Should we return the default value on conversion errors?
         */
        private final boolean useDefault;

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
        @Override
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

            if (value instanceof Long)
            {
                return value;
            }
            else if (value instanceof Number n)
            {
                try
                {
                    return BigDecimal.valueOf(n.doubleValue()).longValueExact();
                }
                catch (Exception e)
                {
                    throw new ConversionException("Could not convert '" + value + "' to a long", e);
                }
            }

            String s = value.toString().trim();
            try
            {
                try
                {
                    return Long.parseLong(s);
                }
                catch (NumberFormatException x)
                {
                    return (new BigDecimal(s)).longValueExact();
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
                    throw new ConversionException("Could not convert '" + value + "' to a long", e);
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
        private final Class<?> _class;

        ConvertUtilsEditor(Class<?> c)
        {
            _class = c;
        }

        @Override
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

        @Override
        public String getAsText()
        {
            Object v = getValue();
            return v == null ? null : ConvertUtils.convert(getValue());
        }
    }


    public static class ColorConverter implements Converter
    {
        @Override
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
        private final org.apache.commons.beanutils.converters.StringArrayConverter _nested =
                new org.apache.commons.beanutils.converters.StringArrayConverter();

        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof String[])
                return value;
            if (value instanceof String s)
            {
                // If the value is wrapped with { and }, let the beanutils converter tokenize the values.
                // This let's us handle Issue 5340 while allowing multi-value strings to be parsed.
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
        @Override
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
        @Override
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
                return new JSONObject((Map<?, ?>)value);

            if (value instanceof String)
                return new JSONObject((String)value);

            throw new ConversionExceptionWithMessage("Could not convert '" + value + "' to a JSONObject");
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
                return Double.valueOf(((Number) value).doubleValue());
            }

            String s = value.toString();
            try
            {
                return Double.valueOf(s);
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
        @Override
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
                    if (ordinal >= 0 && ordinal < values.length)
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
            assertEquals("Wrong date", DateUtil.getDateOnly(cal.getTime()), DateUtil.getDateOnly((Timestamp)convertedDate));
        }

        @Test
        public void testConvertBooleans()
        {
            assertEquals(true, ConvertUtils.convert("true", Boolean.class));
            assertEquals(true, ConvertUtils.convert("t", Boolean.class));
            assertEquals(true, ConvertUtils.convert(" true ", Boolean.class));
            assertEquals(true, ConvertUtils.convert(" t ", Boolean.class));

            assertEquals(false, ConvertUtils.convert("false", Boolean.class));
            assertEquals(false, ConvertUtils.convert("f", Boolean.class));
            assertEquals(false, ConvertUtils.convert(" false ", Boolean.class));
            assertEquals(false, ConvertUtils.convert(" f ", Boolean.class));
        }

        /** Issue 51305: insertRows does not trim before attempting to parse integers */
        @Test
        public void testConvertNumbers()
        {
            assertEquals(100, ConvertUtils.convert("100", Integer.class));
            assertEquals(100, ConvertUtils.convert(" 100 ", Integer.class));
            // One more than maximum
            try
            {
                ConvertUtils.convert("2147483648", Integer.class);
                fail("Should have failed to parse");
            }
            catch (ConversionException ignored) {}

            assertEquals(100L, ConvertUtils.convert("100", Long.class));
            assertEquals(2147483648L, ConvertUtils.convert("2147483648", Long.class));
            assertEquals(100L, ConvertUtils.convert(" 100 ", Long.class));
            // One more than maximum
            try
            {
                ConvertUtils.convert("9223372036854775808", Long.class);
                fail("Should have failed to parse");
            }
            catch (ConversionException ignored) {}

            assertEquals(12.3, ConvertUtils.convert("12.3", Double.class));
            assertEquals(12.3, ConvertUtils.convert(" 12.3 ", Double.class));

            assertEquals(12.3f, ConvertUtils.convert("12.3", Float.class));
            assertEquals(12.3f, ConvertUtils.convert(" 12.3 ", Float.class));
        }
    }

    // Note: Keep in sync with LabKeySiteWrapper.getConversionErrorMessage()
    // Example: "Could not convert value '2.34' (Double) for Boolean field 'Medical History.Dep Diagnosed in Last 18 Months'"
    public static String getStandardConversionErrorMessage(Object value, String fieldName, Class<?> expectedClass)
    {
        return "Could not convert value '" + value + "' (" + value.getClass().getSimpleName() + ") for " + expectedClass.getSimpleName() + " field '" + fieldName + "'";
    }
}
