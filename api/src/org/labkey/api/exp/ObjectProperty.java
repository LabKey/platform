package org.labkey.api.exp;

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.attachments.AttachmentFile;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.Date;
import java.util.Map;
import java.util.Collections;
import java.io.File;
import java.sql.SQLException;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Oct 25, 2005
 * Time: 1:22:35 PM
 */
public class ObjectProperty extends OntologyManager.PropertyRow
{
    private int hashCode = 0;
    public static final int STRING_LENGTH = 4000;

    // Object fields
//    private int objectId = 0;
    private String container;
    private String objectURI;
    private Integer objectOwnerId;

    // PropertyDescriptor
//	private int propertyId = 0;
	private String propertyURI;
    private String name;
    private String rangeURI;
    private String conceptURI;
    private String format;

    // ObjectProperty
//    private char typeTag;
//    private Double floatValue;
//    private String stringValue;
//    private String textValue;
//    private Date dateTimeValue;
    private Identifiable objectValue;
    private Map<String, ObjectProperty> _childProperties;

    public ObjectProperty()
    {
    }


    public ObjectProperty(String objectURI, String container, String propertyURI, String value)
    {
        init(objectURI, container, propertyURI, PropertyType.STRING);
        this.stringValue = value;
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, File value)
    {
        init(objectURI, container, propertyURI, PropertyType.FILE_LINK);
        this.stringValue = value.getPath();
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Date value)
    {
        init(objectURI, container, propertyURI, PropertyType.DATE_TIME);
        this.dateTimeValue = value;
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Double value)
    {
        init(objectURI, container, propertyURI, PropertyType.DOUBLE);
        this.floatValue = value;
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Integer value)
    {
        init(objectURI, container, propertyURI, PropertyType.INTEGER);
        this.floatValue = value.doubleValue();
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Identifiable value)
    {
        init(objectURI, container, propertyURI, PropertyType.RESOURCE);
        this.stringValue = value.getLSID();
        this.objectValue = value;
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Object value)
    {
        this(objectURI, container, propertyURI, value, (String)null);
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Object value, String name)
    {
        this(objectURI, container, propertyURI, value, PropertyType.getFromClass(value.getClass()), name);
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Object value, PropertyType propertyType)
    {
        this(objectURI, container, propertyURI, value, propertyType, null);
    }

    public ObjectProperty(String objectURI, String container, String propertyURI, Object value, PropertyType propertyType, String name)
    {
        init(objectURI, container, propertyURI, propertyType, value);
        setName(name);
    }


    private void init(String objectURI, String container, String propertyURI, PropertyType propertyType)
    {
        this.objectURI = objectURI;
        this.container = container;
        this.propertyURI = propertyURI;
        this.typeTag = propertyType.getStorageType();
        //TODO: For resource, need to override with known type
        this.rangeURI = propertyType.getTypeUri();
    }


    // UNODNE: part of this is duplicate with PropertyRow()
    private void init(String objectURI, String container, String propertyURI, PropertyType propertyType, Object value)
    {
        this.objectURI = objectURI;
        this.container = container;
        this.propertyURI = propertyURI;
        this.typeTag = propertyType.getStorageType();
        //TODO: For resource, need to override with known type
        this.rangeURI = propertyType.getTypeUri();
        switch (propertyType)
        {
            case STRING:
            case MULTI_LINE:
                this.stringValue = (String) value;
                break;
            case ATTACHMENT:
                if (value instanceof AttachmentFile)
                    this.stringValue = ((AttachmentFile) value).getFilename();
                else
                    this.stringValue = (String) value;
                break;
            case FILE_LINK:
                if (value instanceof File)
                    this.stringValue = ((File) value).getPath();
                else
                    this.stringValue = (String) value;
                break;
            case DATE_TIME:
                if (value instanceof Date)
                    this.dateTimeValue = (Date) value;
                else if (null != value)
                    this.dateTimeValue = (Date) ConvertUtils.convert(value.toString(), Date.class);
                break;
            case INTEGER:
                if (value instanceof Integer)
                    this.floatValue = ((Integer) value).doubleValue();
                else if (null != value)
                    this.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
                break;
            case DOUBLE:
                if (value instanceof Double)
                    this.floatValue = (Double) value;
                else if (null != value)
                    this.floatValue = (Double) ConvertUtils.convert(value.toString(), Double.class);
                break;
            case BOOLEAN:
                boolean boolValue = false;
                if (value instanceof Boolean)
                    boolValue = (Boolean)value;
                else if (null != value)
                    boolValue = (Boolean) ConvertUtils.convert(value.toString(), Boolean.class);
                this.floatValue = boolValue ? 1.0 : 0.0;
                break;
            case RESOURCE:
                if (value instanceof Identifiable)
                {
                    this.stringValue = ((Identifiable) value).getLSID();
                    this.objectValue = (Identifiable) value;
                }
                else if (null != value)
                    this.stringValue = value.toString();

                break;
            default:
                throw new IllegalArgumentException("Unknown property type: " + propertyType);
        }
    }

    public Object value()
    {
        switch (getPropertyType())
        {
            case STRING:
            case MULTI_LINE:
                return getEitherStringValue();

            case XML_TEXT:
                return getEitherStringValue();

            case DATE_TIME:
                return dateTimeValue;

            case ATTACHMENT:
                return getEitherStringValue();

            case FILE_LINK:
                String value = getEitherStringValue();
                return value == null ? null : new File(value);

            case INTEGER:
                return floatValue == null ? null : floatValue.intValue();

            case BOOLEAN:
                return floatValue == null ? null : floatValue.intValue() != 0 ? Boolean.TRUE : Boolean.FALSE;

            case DOUBLE:
                return floatValue;

            case RESOURCE:
                if (null != objectValue)
                    return objectValue;
                else
                    return getEitherStringValue();
        }

        throw new IllegalStateException("Unknown data type: " + rangeURI);
    }

    public PropertyType getPropertyType()
    {
        return PropertyType.getFromURI(getConceptURI(), getRangeURI());
    }

    public int getObjectId()
    {
        return objectId;
    }

    public void setObjectId(int objectId)
    {
        this.objectId = objectId;
    }

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }

    public String getObjectURI()
    {
        return objectURI;
    }

    public void setObjectURI(String objectURI)
    {
        this.objectURI = objectURI;
    }

    public Integer getObjectOwnerId()
    {
        return objectOwnerId;
    }

    public void setObjectOwnerId(Integer objectOwnerId)
    {
        this.objectOwnerId = objectOwnerId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPropertyURI()
    {
        return propertyURI;
    }

    public void setPropertyURI(String propertyURI)
    {
        this.propertyURI = propertyURI;
    }

    public String getRangeURI()
    {
        return rangeURI;
    }

    public void setRangeURI(String datatypeURI)
    {
        this.rangeURI = datatypeURI;
    }

    public char getTypeTag()
    {
        return typeTag;
    }

    public void setTypeTag(char typeTag)
    {
        this.typeTag = typeTag;
    }

    public Double getFloatValue()
    {
        return floatValue;
    }

    public void setFloatValue(Double floatValue)
    {
        this.floatValue = floatValue;
    }

    public String getStringValue()
    {
        return stringValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    /** @deprecated */
    public String getEitherStringValue()
    {
        return getStringValue();
    }

    public Date getDateTimeValue()
    {
        return dateTimeValue;
    }

    public void setDateTimeValue(Date dateTimeValue)
    {
        this.dateTimeValue = dateTimeValue;
    }

    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }
    
    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        if (!(o instanceof ObjectProperty))
            return false;

        ObjectProperty pv = (ObjectProperty) o;
        if (pv.getObjectId() != objectId ||
                !pv.getPropertyURI().equals(propertyURI))
            return false;

        Object value = value();

        return value == null ? pv.value() == null : value.equals(pv.value());
    }

    public int hashCode()
    {
        if (0 == hashCode)
        {
            String hashString = objectURI + propertyURI + String.valueOf(value());
            hashCode = hashString.hashCode();
        }

        return hashCode;
    }

    public String getConceptURI()
    {
        return conceptURI;
    }

    public void setConceptURI(String conceptURI)
    {
        this.conceptURI = conceptURI;
    }

    public void setChildProperties(Map<String, ObjectProperty> childProperties)
    {
        _childProperties = childProperties;
    }

    public Map<String, ObjectProperty> retrieveChildProperties() throws SQLException
    {
        if (_childProperties == null)
        {
            if (getPropertyType() == PropertyType.RESOURCE)
            {
                _childProperties = OntologyManager.getPropertyObjects(getContainer(), getStringValue());
            }
            else
            {
                _childProperties = Collections.emptyMap();
            }
        }
        return _childProperties;
    }

	public int getPropertyId()
	{
		return propertyId;
	}

	public void setPropertyId(int propertyId)
	{
		this.propertyId = propertyId;
	}

	public static class ObjectPropertyObjectFactory extends BeanObjectFactory<ObjectProperty>
	{

		public ObjectPropertyObjectFactory()
		{
			super(ObjectProperty.class);
		}

		@Override
		protected void fixupBean(ObjectProperty objProp)
		{
			super.fixupBean(objProp);
			//Equality between Date and Timestamp doesn't work properly so make sure this is a date!
			if (objProp.getPropertyType() == PropertyType.DATE_TIME && objProp.getDateTimeValue() instanceof java.sql.Timestamp)
				objProp.setDateTimeValue(new Date(objProp.getDateTimeValue().getTime()));
		}
	}
}
