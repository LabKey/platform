/*
 * Copyright (c) 2005-2011 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.fhcrc.cpas.exp.xml.SimpleValueType;
import org.labkey.api.collections.BoundMap;
import org.labkey.api.util.DateUtil;

import java.util.Date;

/**
 * User: jeckels
 * Date: Sep 28, 2005
 */
public abstract class AbstractParameter extends BoundMap
{
    public static final String ISO8601_LOCAL_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String SIMPLE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private long _rowId;
    private String _name;
    private String _valueType;
    private String _stringValue;
    private Integer _integerValue;
    private Double _doubleValue;
    private Date _dateTimeValue;
    private String _ontologyEntryURI;

    public AbstractParameter()
    {
        setBean(this);
    }

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getValueType()
    {
        return _valueType;
    }

    public void setValueType(String valueType)
    {
        _valueType = valueType;
    }

    public String getStringValue()
    {
        return _stringValue;
    }

    public void setStringValue(String stringValue)
    {
        _stringValue = stringValue;
    }

    public Integer getIntegerValue()
    {
        return _integerValue;
    }

    public void setIntegerValue(Integer integerValue)
    {
        _integerValue = integerValue;
    }

    public Double getDoubleValue()
    {
        return _doubleValue;
    }

    public void setDoubleValue(Double doubleValue)
    {
        _doubleValue = doubleValue;
    }

    public Date getDateTimeValue()
    {
        return _dateTimeValue;
    }

    public void setDateTimeValue(Date dateTimeValue)
    {
        _dateTimeValue = dateTimeValue;
    }

    public String getOntologyEntryURI()
    {
        return _ontologyEntryURI;
    }

    public void setOntologyEntryURI(String ontologyEntryURI)
    {
        _ontologyEntryURI = ontologyEntryURI;
    }

    public void setValue(SimpleTypeNames.Enum type, Object value)
    {
        setValueType(type.toString());
        if (type == SimpleTypeNames.STRING)
        {
            setStringValue((String) value);
        }
        else if (type == SimpleTypeNames.INTEGER)
        {
            setIntegerValue((Integer) value);
        }
        else if (type == SimpleTypeNames.DOUBLE)
        {
            setDoubleValue((Double) value);
        }
        else if (type == SimpleTypeNames.DATE_TIME)
        {
            setDateTimeValue((Date) value);
        }
        else if (type == SimpleTypeNames.FILE_LINK)
        {
            setStringValue((String) value);
        }
        else
        {
            throw new IllegalArgumentException("Unknown property type " + type);
        }
    }

    public void setValue(PropertyType type, Object value)
    {
        setValueType(type.getXmlBeanType().toString());
        switch (type)
        {
            case STRING:
                setStringValue((String) value);
                break;
            case INTEGER:
                setIntegerValue((Integer) value);
                break;
            case DOUBLE:
                setDoubleValue((Double) value);
                break;
            case DATE_TIME:
                setDateTimeValue((Date) value);
                break;
            case FILE_LINK:
                setStringValue((String) value);
                break;
            default:
                throw new IllegalArgumentException("Unknown property type '" + type + "' for property: " + getName());
        }
    }

    public String getXmlBeanValue()
    {
        Object value = getValue();
        if (value == null)
        {
            return null;
        }
        if (getXmlBeanValueType() == SimpleTypeNames.DATE_TIME)
        {
            Date d = getDateTimeValue();
            // Todo - figure out the right format
            return DateUtil.formatDateTime(d, ISO8601_LOCAL_PATTERN);
        }
        return value.toString();
    }

    public SimpleTypeNames.Enum getXmlBeanValueType()
    {
        return SimpleTypeNames.Enum.forString(getValueType());
    }

    protected void handleOtherValueTypesSetter(SimpleTypeNames.Enum type, Object value)
    {
        throw new IllegalArgumentException("Unknown property type " + type);
    }

    public Object getValue()
    {
        SimpleTypeNames.Enum valueType = getXmlBeanValueType();
        if (SimpleTypeNames.STRING == valueType)
        {
            return getStringValue();
        }
        if (SimpleTypeNames.INTEGER == valueType)
        {
            return getIntegerValue();
        }
        if (SimpleTypeNames.DOUBLE == valueType)
        {
            return getDoubleValue();
        }
        if (SimpleTypeNames.DATE_TIME == valueType)
        {
            return getDateTimeValue();
        }
        if (SimpleTypeNames.FILE_LINK == valueType)
        {
            return getStringValue();
        }
        return handleOtherValueTypesGetter(valueType);
    }

    protected Object handleOtherValueTypesGetter(SimpleTypeNames.Enum type)
    {
        throw new IllegalArgumentException("Unknown property type " + type);
    }

    private String trimString(String s)
    {
        return s == null ? null : s.trim();
    }

    public void setXMLBeanValue(SimpleValueType xbSimpleVal, Logger log)
    {
        setName(xbSimpleVal.getName());
        if (xbSimpleVal.isSetOntologyEntryURI())
            setOntologyEntryURI(xbSimpleVal.getOntologyEntryURI());

        SimpleTypeNames.Enum type = xbSimpleVal.getValueType();
        if (null == type)
            type = SimpleTypeNames.STRING;

        String stringValue = xbSimpleVal.isNil() ? null : trimString(xbSimpleVal.getStringValue());

        Object val;
        try
        {
            if (stringValue == null)
                val = stringValue;
            else if (type.equals(SimpleTypeNames.INTEGER))
                val = new Integer(stringValue);
            else if (type.equals(SimpleTypeNames.DATE_TIME))
                val = new Date(DateUtil.parseDateTime(stringValue));
            else if (type.equals(SimpleTypeNames.DOUBLE))
                val = new Double(stringValue);
            else if (type.equals(SimpleTypeNames.BOOLEAN))
                val = Boolean.valueOf(stringValue);
            else if (type.equals(SimpleTypeNames.FILE_LINK))
                val = stringValue;
            else if (type.equals(SimpleTypeNames.PROPERTY_URI))
                val = stringValue;
            else
            {
                if (!type.equals(SimpleTypeNames.STRING))
                    log.error("Unrecognized valueType '" + type + "' saved as string");
                type = SimpleTypeNames.STRING;
                val = stringValue;
            }
        }
        catch (ConversionException e)
        {
            log.error("Failed to load value " + stringValue
                    + ". Declared as type ;" + type + "' Saved as string instead");
            type = SimpleTypeNames.STRING;
            val = stringValue;
        }
        catch (NumberFormatException e)
        {
            log.error("Failed to load value " + stringValue
                    + ". Declared as type ;" + type + "' Saved as string instead");
            type = SimpleTypeNames.STRING;
            val = stringValue;
        }
        setValue(type, val);
    }
}
