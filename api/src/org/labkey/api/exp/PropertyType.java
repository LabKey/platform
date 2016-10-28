/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
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
    BOOLEAN("http://www.w3.org/2001/XMLSchema#boolean", "Boolean", 'f', JdbcType.BOOLEAN, 10, null, Cell.CELL_TYPE_BOOLEAN, Boolean.class, Boolean.TYPE)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.BOOLEAN;
                }
            },
    STRING("http://www.w3.org/2001/XMLSchema#string", "String", 's', JdbcType.VARCHAR, 4000, "text", Cell.CELL_TYPE_STRING, String.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.STRING;
                }
            },
    MULTI_LINE("http://www.w3.org/2001/XMLSchema#multiLine", "MultiLine", 's', JdbcType.VARCHAR, 4000, "textarea", Cell.CELL_TYPE_STRING, String.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.STRING;
                }
            },
    RESOURCE("http://www.w3.org/2000/01/rdf-schema#Resource", "PropertyURI", 's', JdbcType.VARCHAR, 4000, null, Cell.CELL_TYPE_STRING, Identifiable.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.STRING;
                }
            },
    INTEGER("http://www.w3.org/2001/XMLSchema#int", "Integer", 'f', JdbcType.INTEGER, 10, null, Cell.CELL_TYPE_NUMERIC, Integer.class, Integer.TYPE, Long.class, Long.TYPE)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.INTEGER;
                }
            },
    BIGINT("http://www.w3.org/2001/XMLSchema#long", "Long", 'f', JdbcType.BIGINT, 10, null, Cell.CELL_TYPE_NUMERIC, Long.class, Long.TYPE)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            },
    BINARY("http://www.w3.org/2001/XMLSchema#binary", "Binary", 'f', JdbcType.BINARY, 10, null, Cell.CELL_TYPE_NUMERIC, ByteBuffer.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            },    /** Stored as a path to a file on the server's file system */
    FILE_LINK("http://cpas.fhcrc.org/exp/xml#fileLink", "FileLink", 's', JdbcType.VARCHAR, 400, "file", Cell.CELL_TYPE_STRING, File.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.FILE_LINK;
                }
            },
    /** Stored in the database as a BLOB using AttachmentService */
    ATTACHMENT("http://www.labkey.org/exp/xml#attachment", "Attachment", 's', JdbcType.VARCHAR, 100, "file", Cell.CELL_TYPE_STRING, File.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            },
    DATE_TIME("http://www.w3.org/2001/XMLSchema#dateTime", "DateTime", 'd', JdbcType.TIMESTAMP, 100, null, Cell.CELL_TYPE_NUMERIC, Date.class)
            {
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
                        return ConvertUtils.convert(value.toString(), Date.class);
                }

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.DATE_TIME;
                }
            },
    DATE("http://www.w3.org/2001/XMLSchema#date", "Date", 'd', JdbcType.DATE, 100, null, Cell.CELL_TYPE_NUMERIC, Date.class)
            {
                protected Object convertExcelValue(Cell cell) throws ConversionException
                {
                    return DateUtil.getDateOnly((Date)DATE_TIME.convertExcelValue(cell));
                }

                @Override
                public Object convert(Object value) throws ConversionException
                {
                    return DateUtil.getDateOnly((Date)DATE_TIME.convert(value));
                }

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.DATE_TIME;
                }
            },
    TIME("http://www.w3.org/2001/XMLSchema#time", "Time", 'd', JdbcType.TIME, 100, null, Cell.CELL_TYPE_NUMERIC, Date.class)
            {
                protected Object convertExcelValue(Cell cell) throws ConversionException
                {
                    return DateUtil.getTimeOnly((Date)DATE_TIME.convertExcelValue(cell));
                }

                @Override
                public Object convert(Object value) throws ConversionException
                {
                    return DateUtil.getTimeOnly((Date)DATE_TIME.convert(value));
                }

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.DATE_TIME;
                }
            },
    DOUBLE("http://www.w3.org/2001/XMLSchema#double", "Double", 'f', JdbcType.DOUBLE, 20, null, Cell.CELL_TYPE_NUMERIC, Double.class, Double.TYPE, Float.class, Float.TYPE)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    return SimpleTypeNames.DOUBLE;
                }
            },
    FLOAT("http://www.w3.org/2001/XMLSchema#float", "Float", 'f', JdbcType.REAL, 20, null, Cell.CELL_TYPE_NUMERIC, Float.class, Float.TYPE)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            },
    DECIMAL("http://www.w3.org/2001/XMLSchema#decimal", "Decimal", 'f', JdbcType.DECIMAL, 20, null, Cell.CELL_TYPE_NUMERIC, BigDecimal.class)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            },
    XML_TEXT("http://cpas.fhcrc.org/exp/xml#text-xml", "XmlText", 's', JdbcType.LONGVARCHAR, 4000, null, Cell.CELL_TYPE_STRING, null)
            {
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

                public SimpleTypeNames.Enum getXmlBeanType()
                {
                    throw new UnsupportedOperationException();
                }
            };

    private String typeURI;
    private String xarName;
    private char storageType;
    private int excelCellType;
    @NotNull private JdbcType jdbcType;
    private int scale;
    private String inputType;
    private Class javaType;
    private Class[] additionalTypes;

    private static Map<String, PropertyType> uriToProperty = null;
    private static Map<String, PropertyType> xarToProperty = null;

    PropertyType(String typeURI,
                 String xarName,
                 char storageType,
                 @NotNull JdbcType jdbcType,
                 int scale,
                 String inputType,
                 int excelCellType,
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

    public int getSqlType()
    {
        return jdbcType.sqlType;
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
        throw new IllegalArgumentException("No such class mapping: " + clazz.getName());
    }

    @NotNull
    public static PropertyType getFromJdbcType(JdbcType jdbcType)
    {
        for (PropertyType t : values())
        {
            if (t.jdbcType == null)
                continue;
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
            if (t.jdbcType == null)
                continue;
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
}
