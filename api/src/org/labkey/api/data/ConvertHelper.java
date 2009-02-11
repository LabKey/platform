/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.beanutils.converters.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.GuidString;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.common.tools.AbstractConvertHelper;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import java.awt.*;
import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;


public class ConvertHelper extends AbstractConvertHelper implements PropertyEditorRegistrar
{
    private static final Logger _log = Logger.getLogger(ConvertHelper.class);
    private static ConvertHelper _myInstance = null;

    // just a list of converters we know about
    HashSet<Class> _converters = new HashSet<Class>();
    
    
    public static void registerHelpers()
    {
        // Should register only once
        assert null == _myInstance;

        _myInstance = new ConvertHelper();
        _myInstance.register();
    }


    public static PropertyEditorRegistrar getPropertyEditorRegistrar()
    {
        if (null == _myInstance)
            registerHelpers();
        return _myInstance;
    }


    private ConvertHelper()
    {
    }


    // Register shared converters, then cpas-specific converters
    // TODO: Merge some or all of the converters with msInspect converters
    protected void register()
    {
        super.register();

        _register(new BooleanConverter(), Boolean.TYPE);
        _register(new NullSafeConverter(new BooleanConverter()), Boolean.class);
        _register(new NullSafeConverter(new SQLByteArrayConverter()), byte[].class);
        _register(new DoubleConverter(), Double.TYPE);
        _register(new FloatConverter(), Float.TYPE);
        _register(new NullSafeConverter(new FloatConverter()), Float.class);
        _register(new _IntegerConverter(), Integer.TYPE);
        _register(new NullSafeConverter(new _IntegerConverter()), Integer.class);
        _register(new ContainerConverter(), org.labkey.api.data.Container.class);
        _register(new NullSafeConverter(new DoubleConverter()), Double.class);
        _register(new NullSafeConverter(new DateFriendlyStringConverter()), String.class);
        _register(new LenientTimestampConverter(), java.sql.Timestamp.class);
        _register(new LenientDateConverter(), java.util.Date.class);
        _register(new ColorConverter(), Color.class);
        _register(new StringArrayConverter(), String[].class);
        _register(new ReportIdentifierConverter(), ReportIdentifier.class);

        _register(new HString.Converter(), HString.class);
        _register(new GuidString.Converter(), GuidString.class);
		_register(new IdentifierString.Converter(), IdentifierString.class);
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


    public static class LenientTimestampConverter implements Converter
    {
        private LenientDateConverter _dateConverter = new LenientDateConverter();

        public Object convert(Class clss, Object o)
        {
            if (null == o)
                return null;

            if (o instanceof Timestamp)
                return o;

            return new Timestamp(((Date) _dateConverter.convert(Date.class, o)).getTime());
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

            // Note: When using getObject() on a SQL column of type Text or nText, the Microsoft SQL Server jdbc driver returns
            // a String, while the jTDS driver returns a Clob.  So, we need to handle Clob to test jTDS and potentially
            // other drivers that return Clobs.
            if (o instanceof Clob)
            {
                Clob clob = (Clob) o;

                try
                {
                    return clob.getSubString(1, (int) clob.length());
                }
                catch (SQLException e)
                {
                    _log.error(e);
                }
            }

            return _stringConverter.convert(String.class, o);
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
                c = ContainerManager.getForPath(str);
            else if (str.startsWith(Container.class.getName() + "@") && str.indexOf(" ") != -1)
            {
                // Sometimes we get called with a Container.toString() value since the Apache conversion code
                // doesn't appear to handle a no-op conversion on Containers - it calls toString() to force us to convert
                String id = str.substring(str.lastIndexOf(' ') + 1);
                c = ContainerManager.getForId(id);
            }
            else
                c = ContainerManager.getForId(str);

            if (null == c)
                throw new ConversionException("Could not convert: " + str + " to container.");

            return c;
        }
    }


    public static class SQLByteArrayConverter implements Converter
    {
        // used for "getClass" comparison
        static final private byte[] _byteArray = new byte[0];

        final Converter _converter;

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
            if (clazz != _byteArray.getClass())
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
                    throw new ConversionException(e);
                }
            }
        }
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
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            return new String[] {String.valueOf(value)};
        }
    }
}
