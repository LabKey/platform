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

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * A single object-property-value triple.
 * User: migra
 * Date: Oct 25, 2005
 */
public class ObjectProperty extends OntologyManager.PropertyRow
{
    private int hashCode = 0;

    // Object fields
    private Container container;
    private String objectURI;
    private Integer objectOwnerId;

    // PropertyDescriptor
	private String propertyURI;
    private String name;
    private String rangeURI;
    private String conceptURI;
    private String format;

    // ObjectProperty
    protected Identifiable objectValue;
    private Map<String, ObjectProperty> _childProperties;

    // Don't delete this -- it's accessed via introspection
    public ObjectProperty()
    {
    }
    
    public ObjectProperty(String objectURI, Container container, String propertyURI, String value)
    {
        init(objectURI, container, propertyURI, PropertyType.STRING);
        this.stringValue = value;
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, File value)
    {
        init(objectURI, container, propertyURI, PropertyType.FILE_LINK);
        this.stringValue = value.getPath();
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Date value)
    {
        init(objectURI, container, propertyURI, PropertyType.DATE_TIME);
        this.dateTimeValue = value;
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Double value)
    {
        init(objectURI, container, propertyURI, PropertyType.DOUBLE);
        this.floatValue = value;
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Integer value)
    {
        init(objectURI, container, propertyURI, PropertyType.INTEGER);
        this.floatValue = value.doubleValue();
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Identifiable value)
    {
        init(objectURI, container, propertyURI, PropertyType.RESOURCE);
        this.stringValue = value.getLSID();
        this.objectValue = value;
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Object value)
    {
        this(objectURI, container, propertyURI, value, (String)null);
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Object value, String name)
    {
        this(objectURI, container, propertyURI, value, PropertyType.getFromClass(value.getClass()), name);
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Object value, PropertyType propertyType)
    {
        this(objectURI, container, propertyURI, value, propertyType, null);
    }

    public ObjectProperty(String objectURI, Container container, PropertyDescriptor pd, Object value)
    {
        this(objectURI, container, pd.getPropertyURI(), value, pd.getPropertyType(), pd.getName());
    }

    public ObjectProperty(String objectURI, Container container, String propertyURI, Object value, PropertyType propertyType, String name)
    {
        init(objectURI, container, propertyURI, propertyType, value);
        setName(name);
    }


    private void init(String objectURI, Container container, String propertyURI, PropertyType propertyType)
    {
        this.objectURI = objectURI;
        this.container = container;
        this.propertyURI = propertyURI;
        this.typeTag = propertyType.getStorageType();
        //TODO: For resource, need to override with known type
        this.rangeURI = propertyType.getTypeUri();
    }


    // UNODNE: part of this is duplicate with PropertyRow()
    private void init(String objectURI, Container container, String propertyURI, PropertyType propertyType, Object value)
    {
        this.objectURI = objectURI;
        this.container = container;
        this.propertyURI = propertyURI;
        this.typeTag = propertyType.getStorageType();
        //TODO: For resource, need to override with known type
        this.rangeURI = propertyType.getTypeUri();
        if (value instanceof MvFieldWrapper)
        {
            MvFieldWrapper wrapper = (MvFieldWrapper)value;
            this.mvIndicator = wrapper.getMvIndicator();
            value = wrapper.getValue();
        }

        propertyType.setValue(this, value);
    }

    public Object getValueMvAware()
    {
        Object value = value();
        if (mvIndicator == null)
            return value;
        return new MvFieldWrapper(MvUtil.getMvIndicators(container), value, mvIndicator);
    }

    public Object value()
    {
        return getPropertyType().getValue(this);
    }

    public PropertyType getPropertyType()
    {
        return PropertyType.getFromURI(getConceptURI(), getRangeURI());
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
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
        if (pv.getObjectId() != objectId || !pv.getPropertyURI().equals(propertyURI))
            return false;

        Object value = value();

        return value == null ? pv.value() == null : value.equals(pv.value());
    }

    public int hashCode()
    {
        if (0 == hashCode)
        {
            String hashString = objectURI + propertyURI + value();
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

    public Map<String, ObjectProperty> retrieveChildProperties()
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
