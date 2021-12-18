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

package org.labkey.api.exp;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.OntologyManager.PropertyRow;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.util.DateUtil;

import java.io.File;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * User: migra
 * Date: Oct 25, 2005
 *
 * TODO: Add more types? Entity, Lsid, User, ...
 */
public enum PropertyType
{
    BOOLEAN("http://www.w3.org/2001/XMLSchema#boolean", "Boolean", 'f', JdbcType.BOOLEAN, 10, null, CellType.BOOLEAN, Boolean.class, Boolean.TYPE)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getBooleanCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            boolean boolValue = false;
            if (value instanceof Boolean)
                boolValue = (Boolean)value;
            else if (null != value && !"".equals(value))
                boolValue = (Boolean) ConvertUtils.convert(value.toString(), Boolean.class);
            return boolValue;
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.BOOLEAN;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Boolean b = (Boolean)value;
            row.floatValue = b == Boolean.TRUE ? 1.0 : 0.0;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            Boolean boolValue = null;
            if (value instanceof Boolean)
                boolValue = (Boolean)value;
            else if (null != value)
                boolValue = (Boolean) ConvertUtils.convert(value.toString(), Boolean.class);
            property.floatValue = boolValue == null ? null : boolValue == Boolean.TRUE ? 1.0 : 0.0;
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue == null ? null : property.floatValue.intValue() != 0 ? Boolean.TRUE : Boolean.FALSE;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return Boolean.TRUE;
        }
    },
    STRING("http://www.w3.org/2001/XMLSchema#string", "String", 's', JdbcType.VARCHAR, 4000, "text", CellType.STRING, String.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.STRING;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.stringValue = (String)value;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            property.stringValue = value == null ? null : value.toString();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.getStringValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    },
    MULTI_LINE("http://www.w3.org/2001/XMLSchema#multiLine", "MultiLine", 's', JdbcType.VARCHAR, 4000, "textarea", CellType.STRING, String.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.STRING;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.stringValue = (String)value;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            property.stringValue = value == null ? null : value.toString();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.getStringValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    },
    RESOURCE("http://www.w3.org/2000/01/rdf-schema#Resource", "PropertyURI", 's', JdbcType.VARCHAR, 4000, null, CellType.STRING, Identifiable.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Identifiable)
                return ((Identifiable) value).getLSID();
            else
                return value.toString();
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.STRING;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.stringValue = (String)value;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Identifiable)
            {
                property.stringValue = ((Identifiable) value).getLSID();
                property.objectValue = (Identifiable) value;
            }
            else if (null != value)
                property.stringValue = value.toString();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            if (null != property.objectValue)
                return property.objectValue;
            else
                return property.getStringValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    },
    INTEGER("http://www.w3.org/2001/XMLSchema#int", "Integer", 'f', JdbcType.INTEGER, 10, null, CellType.NUMERIC, Integer.class, Integer.TYPE, Long.class, Long.TYPE)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return (int)cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Integer)
                return value;
            else
                return ConvertUtils.convert(value.toString(), Integer.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.INTEGER;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Number n = (Number) value;
            if (null != n)
                row.floatValue = n.doubleValue();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Integer)
                property.floatValue = ((Integer) value).doubleValue();
            else if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue == null ? null : property.floatValue.intValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return Integer.valueOf(3);
        }
    },
    BIGINT("http://www.w3.org/2001/XMLSchema#long", "Long", 'f', JdbcType.BIGINT, 10, null, CellType.NUMERIC, Long.class, Long.TYPE)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return (int)cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Long)
                return value;
            else
                return ConvertUtils.convert(value.toString(), Long.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Number n = (Number) value;
            if (null != n)
                row.floatValue = n.doubleValue();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Long)
                property.floatValue = ((Long) value).doubleValue();
            else if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue == null ? null : property.floatValue.longValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return Integer.valueOf(3);
        }
    },
    BINARY("http://www.w3.org/2001/XMLSchema#binary", "Binary", 'f', JdbcType.BINARY, 10, null, CellType.NUMERIC, ByteBuffer.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return (int)cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof ByteBuffer)
                return value;
            else
                return ConvertUtils.convert(value.toString(), ByteBuffer.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            throw new UnsupportedOperationException();
        }
    },
    /** Stored as a path to a file on the server's file system */
    FILE_LINK("http://cpas.fhcrc.org/exp/xml#fileLink", "FileLink", 's', JdbcType.VARCHAR, 400, "file", CellType.STRING, File.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof File)
                return ((File) value).getPath();
            else
                return String.valueOf(value);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.FILE_LINK;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.stringValue = (String)value;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof File)
                property.stringValue = ((File) value).getPath();
            else
                property.stringValue = value == null ? null : value.toString();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            String value = property.getStringValue();
            return value == null ? null : new File(value);
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    },
    /** Stored in the database as a BLOB using AttachmentService */
    ATTACHMENT("http://www.labkey.org/exp/xml#attachment", "Attachment", 's', JdbcType.VARCHAR, 100, "file", CellType.STRING, File.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof File)
                return ((File) value).getPath();
            else
                return String.valueOf(value);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.stringValue = (String)value;
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof AttachmentFile)
            {
                property.stringValue = ((AttachmentFile)value).getFilename();
            }
            else
                property.stringValue = value == null ? null : value.toString();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.getStringValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    },
    DATE_TIME("http://www.w3.org/2001/XMLSchema#dateTime", "DateTime", 'd', JdbcType.TIMESTAMP, 100, null, CellType.NUMERIC, Date.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            Date date = cell.getDateCellValue();
            if (date != null)
            {
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy GG HH:mm:ss.SSS");
                format.setTimeZone(TimeZone.getDefault());
                String s = format.format(date);
                try
                {
                    date = format.parse(s);
                }
                catch (ParseException e)
                {
                    throw new ConversionException(e);
                }
//                int offset = TimeZone.getDefault().getOffset(date.getTime());
//                date.setTime(date.getTime() - offset);
            }
            return date;
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Date)
                return value;
            else
            {
                String strVal = value.toString();
                if (DateUtil.isSignedDuration(strVal))
                    strVal = JdbcType.TIMESTAMP.convert(value).toString();
                return ConvertUtils.convert(strVal, Date.class);
            }
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.DATE_TIME;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.dateTimeValue = new java.sql.Time(((java.util.Date)value).getTime());
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Date)
                property.dateTimeValue = (Date) value;
            else if (null != value)
                property.dateTimeValue = (Date) ConvertUtils.convert(value.toString(), Date.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.dateTimeValue;
        }


        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            try
            {
                return new SimpleDateFormat("yyyy/MM/dd").parse("2021/04/28");
            }
            catch (ParseException e)
            {
                return null;
            }
        }
    },
    DATE("http://www.w3.org/2001/XMLSchema#date", "Date", 'd', JdbcType.DATE, 100, null, CellType.NUMERIC, Date.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return DateUtil.getDateOnly((Date)DATE_TIME.convertExcelValue(cell));
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            return DateUtil.getDateOnly((Date)DATE_TIME.convert(value));
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.DATE_TIME;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.dateTimeValue = new java.sql.Date(((java.util.Date)value).getTime());
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Date)
                property.dateTimeValue = (Date) value;
            else if (null != value)
                property.dateTimeValue = (Date) ConvertUtils.convert(value.toString(), Date.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.dateTimeValue;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            try
            {
                return new SimpleDateFormat("yyyy/MM/dd").parse("2021/04/28");
            }
            catch (ParseException e)
            {
                return null;
            }
        }
    },
    TIME("http://www.w3.org/2001/XMLSchema#time", "Time", 'd', JdbcType.TIME, 100, null, CellType.NUMERIC, Date.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return DateUtil.getTimeOnly((Date)DATE_TIME.convertExcelValue(cell));
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            return DateUtil.getTimeOnly((Date)DATE_TIME.convert(value));
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.DATE_TIME;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            row.dateTimeValue = new java.sql.Time(((java.util.Date)value).getTime());
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.dateTimeValue;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            try
            {
                return new SimpleDateFormat("yyyy/MM/dd").parse("2021/04/28").getTime();
            }
            catch (ParseException e)
            {
                return null;
            }
        }
    },
    DOUBLE("http://www.w3.org/2001/XMLSchema#double", "Double", 'f', JdbcType.DOUBLE, 20, null, CellType.NUMERIC, Double.class, Double.TYPE, Float.class, Float.TYPE)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Double)
                return value;
            else
                return ConvertUtils.convert(String.valueOf(value), Double.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            return SimpleTypeNames.DOUBLE;
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Number n = (Number) value;
            if (null != n)
                row.floatValue = n.doubleValue();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Double)
                property.floatValue = (Double) value;
            else if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return 12.34;
        }
    },
    FLOAT("http://www.w3.org/2001/XMLSchema#float", "Float", 'f', JdbcType.REAL, 20, null, CellType.NUMERIC, Float.class, Float.TYPE)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof Float)
                return value;
            else
                return ConvertUtils.convert(String.valueOf(value), Float.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Number n = (Number) value;
            if (null != n)
                row.floatValue = n.doubleValue();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (value instanceof Double)
                property.floatValue = (Double) value;
            else if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return 12.34;
        }
    },
    DECIMAL("http://www.w3.org/2001/XMLSchema#decimal", "Decimal", 'f', JdbcType.DECIMAL, 20, null, CellType.NUMERIC, BigDecimal.class)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getNumericCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (null == value)
                return null;
            if (value instanceof BigDecimal)
                return value;
            else
                return ConvertUtils.convert(String.valueOf(value), BigDecimal.class);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            Number n = (Number) value;
            if (null != n)
                row.floatValue = n.doubleValue();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            if (null != value)
                property.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.floatValue;
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return 12.34;
        }
    },
    XML_TEXT("http://cpas.fhcrc.org/exp/xml#text-xml", "XmlText", 's', JdbcType.LONGVARCHAR, 4000, null, CellType.STRING, null)
    {
        @Override
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getStringCellValue();
        }

        @Override
        public Object convert(Object value) throws ConversionException
        {
            if (value instanceof String)
                return value;
            else
                return ConvertUtils.convert(value);
        }

        @Override
        public SimpleTypeNames.Enum getXmlBeanType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void init(PropertyRow row, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void setValue(ObjectProperty property, Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object getValue(ObjectProperty property)
        {
            return property.getStringValue();
        }

        @Override
        public Object getPreviewValue(@Nullable String prefix)
        {
            return prefix + "Value";
        }
    };

    private final String typeURI;
    private final String xarName;
    private final char storageType;
    private final CellType excelCellType;
    private final @NotNull JdbcType jdbcType;
    private final int scale;
    private final String inputType;
    private final Class javaType;
    private final Class[] additionalTypes;

    private static Map<String, PropertyType> uriToProperty = null;
    private static Map<String, PropertyType> xarToProperty = null;

    PropertyType(String typeURI,
                 String xarName,
                 char storageType,
                 @NotNull JdbcType jdbcType,
                 int scale,
                 String inputType,
                 CellType excelCellType,
                 Class javaType,
                 Class... additionalTypes)
    {
        this.typeURI = typeURI;
        this.xarName = xarName;
        this.storageType = storageType;
        this.jdbcType = jdbcType;
        this.scale = scale;
        this.inputType = inputType;
        this.javaType = javaType;
        this.excelCellType = excelCellType;
        this.additionalTypes = additionalTypes;
    }

    public String getTypeUri()
    {
        return typeURI;
    }

    public String getXmlName()
    {
        return xarName;
    }

    public char getStorageType()
    {
        return storageType;
    }

    @NotNull
    public JdbcType getJdbcType()
    {
        return jdbcType;
    }

    public int getScale()
    {
        return scale;
    }

    @Nullable
    public String getInputType()
    {
        return inputType;
    }

    public Class getJavaType()
    {
        return javaType;
    }

    public String getXarName()
    {
        return xarName;
    }

    @NotNull
    public static PropertyType getFromURI(String concept, String datatype)
    {
        return getFromURI(concept, datatype, RESOURCE);
    }

    @Deprecated // Eliminate this along with PropertyRow? Or at least combine with setValue() below.
    abstract protected void init(PropertyRow row, Object value);
    abstract protected void setValue(ObjectProperty property, Object value);
    abstract protected Object getValue(ObjectProperty property);
    public Object getPreviewValue(@Nullable String prefix)
    {
        return getValue(null);
    }

    static
    {
        Map<String, PropertyType> m = new HashMap<>();

        for (PropertyType t : values())
        {
            String uri = t.getTypeUri();
            m.put(uri, t);
            m.put(t.getXmlName(), t);

            if (uri.startsWith("http://www.w3.org/2001/XMLSchema#") || uri.startsWith("http://www.labkey.org/exp/xml#"))
            {
                String xsdName = uri.substring(uri.indexOf('#') + 1);
                m.put("xsd:" + xsdName, t);
                m.put(xsdName, t);
            }
        }

        uriToProperty = m;
    }

    public static PropertyType getFromURI(@Nullable String concept, String datatype, PropertyType def)
    {
        PropertyType p = uriToProperty.get(concept);

        if (null == p)
        {
            p = uriToProperty.get(datatype);
            if (null == p)
                p = def;
        }

        return p;
    }

    @NotNull
    public static PropertyType getFromXarName(String xarName)
    {
        return getFromXarName(xarName, RESOURCE);
    }

    public static PropertyType getFromXarName(String xarName, PropertyType def)
    {
        if (null == xarToProperty)
        {
            Map<String, PropertyType> m = new CaseInsensitiveHashMap<>();
            for (PropertyType t : values())
            {
                m.put(t.getXmlName(), t);
            }
            xarToProperty = m;
        }

        PropertyType p = xarToProperty.get(xarName);

        return null == p ? def : p;
    }

    public static PropertyType getFromClass(Class clazz)
    {
        if (clazz == BigDecimal.class)
            clazz = Double.class;

        for (PropertyType t : values())
        {
            if (t.javaType == null)
                continue;
            if (t.javaType.isAssignableFrom(clazz))
                return t;
        }

        // after trying the primary types, we then try any additional types:
        for (PropertyType t : values())
        {
            if (t.additionalTypes == null || t.additionalTypes.length == 0)
                continue;
            for (Class type : t.additionalTypes)
            {
                if (type.isAssignableFrom(clazz))
                    return t;
            }
        }
        return PropertyType.STRING;
    }

    @NotNull
    public static PropertyType getFromJdbcType(JdbcType jdbcType)
    {
        for (PropertyType t : values())
        {
            if (t.jdbcType.equals(jdbcType))
                return t;
        }
        throw new IllegalArgumentException("No such JdbcType mapping: " + (null != jdbcType ? jdbcType.getClass().toString() : "null"));
    }

    @Nullable
    public static PropertyType getFromJdbcTypeName(String typeName)
    {
        for (PropertyType t : values())
        {
            if (typeName.equalsIgnoreCase(t.jdbcType.name()))
                return t;
        }
        return null;
    }

    public abstract SimpleTypeNames.Enum getXmlBeanType();

    protected abstract Object convertExcelValue(Cell cell) throws ConversionException;

    public abstract Object convert(Object value) throws ConversionException;

    public static Object getFromExcelCell(Cell cell) throws ConversionException
    {
        if (ExcelFactory.isCellNumeric(cell))
        {
            // Ugly, the POI implementation doesn't expose an explicit date type
            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell))
                return DATE_TIME.convertExcelValue(cell);
            else
                // special handling for the "number type": prefer double.
                // Without this, we'd default to integer
                return DOUBLE.convertExcelValue(cell);
        }

        for (PropertyType t : values())
        {
            if (t.excelCellType == cell.getCellType())
                return t.convertExcelValue(cell);
        }
        return ExcelFactory.getCellStringValue(cell);
    }

    public String getValueTypeColumn()
    {
        switch (this.getStorageType())
        {
            case 's':
                return "stringValue";
            case 'd':
                return "dateTimeValue";
            case 'f':
                return "floatValue";
            default:
                throw new IllegalArgumentException("Unknown property type: " + this);
        }
    }
}
