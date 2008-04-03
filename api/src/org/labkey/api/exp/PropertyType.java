package org.labkey.api.exp;

import jxl.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.TimeZone;
import java.io.File;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Oct 25, 2005
 * Time: 2:10:08 PM
 */
public enum PropertyType
{
    BOOLEAN("http://www.w3.org/2001/XMLSchema#boolean", "Boolean", 'f', Types.BOOLEAN, 10, null, CellType.BOOLEAN, Boolean.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return ((BooleanCell)cell).getValue();
        }
    },
    STRING("http://www.w3.org/2001/XMLSchema#string", "String", 's', Types.VARCHAR, 100, null, CellType.LABEL, String.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    },
    MULTI_LINE("http://www.w3.org/2001/XMLSchema#multiLine", "MultiLine", 's', Types.VARCHAR, 1000, "textarea", CellType.LABEL, String.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    },
    RESOURCE("http://www.w3.org/2000/01/rdf-schema#Resource", "PropertyURI", 's', Types.VARCHAR, 100, null, CellType.LABEL, Identifiable.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    },
    INTEGER("http://www.w3.org/2001/XMLSchema#int", "Integer", 'f', Types.INTEGER, 10, null, CellType.NUMBER, Integer.class, Long.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return (int)((NumberCell)cell).getValue();
        }
    },
    FILE_LINK("http://cpas.fhcrc.org/exp/xml#fileLink", "FileLink", 's', Types.VARCHAR, 100, "file", CellType.LABEL, File.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    },
    ATTACHMENT("http://www.labkey.org/exp/xml#attachment", "Attachment", 's', Types.VARCHAR, 100, "file", CellType.LABEL, File.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    },
    DATE_TIME("http://www.w3.org/2001/XMLSchema#dateTime", "DateTime", 'd', Types.TIMESTAMP, 100, null, CellType.DATE, Date.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            Date date = ((DateCell) cell).getDate();
            if (date != null)
            {
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy GG HH:mm:ss.SSS");
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                String s = format.format(date);
                format.setTimeZone(TimeZone.getDefault());
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
    },
    DOUBLE("http://www.w3.org/2001/XMLSchema#double", "Double", 'f', Types.DOUBLE, 20, null, CellType.NUMBER, Double.class, Float.class)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return ((NumberCell)cell).getValue();
        }
    },
    XML_TEXT("http://cpas.fhcrc.org/exp/xml#text-xml", "XmlText", 's', Types.LONGVARCHAR, 100, null,CellType.LABEL, null)
    {
        protected Object convertExcelValue(Cell cell) throws ConversionException
        {
            return cell.getContents();
        }
    };

    private String typeURI;
    private String xarName;
    private char storageType;
    private CellType excelCellType;
    private int sqlType;
    private int scale;
    private String inputType;
    private Class javaType;
    private Class[] additionalTypes;

    private static Map<String, PropertyType> uriToProperty = null;
    private static Map<String, PropertyType> xarToProperty = null;

    PropertyType(String typeURI,
                 String xarName,
                 char storageType,
                 int sqlType,
                 int scale,
                 String inputType,
                 CellType excelCellType,
                 Class javaType,
                 Class... additionalTypes)
    {
        this.typeURI = typeURI;
        this.xarName = xarName;
        this.storageType = storageType;
        this.sqlType = sqlType;
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
        return sqlType;
    }

    public int getScale()
    {
        return scale;
    }

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

    public static PropertyType getFromURI(String concept, String datatype)
    {
        return getFromURI(concept, datatype, RESOURCE);
    }


    public static PropertyType getFromURI(String concept, String datatype, PropertyType def)
    {
        if (null == uriToProperty)
        {
            Map<String, PropertyType> m = new HashMap<String, PropertyType>();
            for (PropertyType t : values())
            {
                String uri = t.getTypeUri();
                m.put(uri, t);
                m.put(t.getXmlName(), t);
                if (uri.startsWith("http://www.w3.org/2001/XMLSchema#"))
                {
                    String xsdName = uri.substring("http://www.w3.org/2001/XMLSchema#".length());
                    m.put("xsd:" + xsdName, t);
                    m.put(xsdName, t);
                }
            }
            uriToProperty = m;
        }

        PropertyType p = uriToProperty.get(concept);
        if (null == p)
        {
            p = uriToProperty.get(datatype);
            if (null == p)
                p = def;
        }

        return p;
    }


    public static PropertyType getFromXarName(String xarName)
    {
        if (null == xarToProperty)
        {
            Map<String, PropertyType> m = new HashMap<String, PropertyType>();
            for (PropertyType t : values())
            {
                m.put(t.getXmlName(), t);
            }
            xarToProperty = m;
        }

        PropertyType p = xarToProperty.get(xarName);

        return null == p ? RESOURCE : p;
    }


    public static PropertyType getFromClass(Class clazz)
    {
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

    protected abstract Object convertExcelValue(Cell cell) throws ConversionException;

    public Object getExcelValue(Cell cell) throws ConversionException
    {
        if (excelCellType.equals(cell.getType()))
        {
            return convertExcelValue(cell);
        }
        return ConvertUtils.convert(cell.getContents(), getJavaType());
    }
}
