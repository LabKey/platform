/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.cbcassay.data;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.SystemProperty;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: kevink
 * Date: Nov 19, 2008 3:35:40 PM
 */
public class CBCDataProperty
{
    private static final String PROPERTY_BASE = "urn:cbcassay.labkey.org/#";
    public static final SystemProperty MinValue = new SystemProperty(PROPERTY_BASE + "MinValue", PropertyType.DOUBLE, "MinValue");
    public static final SystemProperty MaxValue = new SystemProperty(PROPERTY_BASE + "MaxValue", PropertyType.DOUBLE, "MaxValue");
    public static final SystemProperty Units = new SystemProperty(PROPERTY_BASE + "Units", PropertyType.STRING, "Units");

    private PropertyDescriptor pd;

    private Map<String, Object> properties = null;
    private Double minValue = null;
    private Double maxValue = null;
    private String units = null;

    public static void register()
    {
        // referring to this method during module startup ensures the SystemProperties are registered
    }

    public CBCDataProperty(PropertyDescriptor pd)
    {
        this.pd = pd;
        assert pd != null : "PropertyDescriptor required";
    }

    public Container getContainer()
    {
        return pd.getContainer();
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        return pd;
    }

    protected Map<String, Object> getProperties()
    {
        if (properties == null)
        {
            properties = OntologyManager.getProperties(getContainer(), pd.getPropertyURI());
        }
        if (properties == null)
            properties = Collections.emptyMap();
        return properties;
    }

    public boolean hasProperties()
    {
        return !getProperties().isEmpty();
    }

    public Object getProperty(String propertyUri)
    {
        return getProperties().get(propertyUri);
    }

    public Double getMinValue()
    {
        if (minValue == null)
            minValue = (Double)getProperty(MinValue.getPropertyDescriptor().getPropertyURI());
        return minValue;
    }

    public Double getMaxValue()
    {
        if (maxValue == null)
            maxValue = (Double)getProperty(MaxValue.getPropertyDescriptor().getPropertyURI());
        return maxValue;
    }

    public String getUnits()
    {
        if (units == null)
            units = (String)getProperty(Units.getPropertyDescriptor().getPropertyURI());
        return units;
    }

    public boolean inRange(Double value)
    {
        if (value == null)
            return false;

        Double minValue = getMinValue();
        if (minValue != null && value < minValue)
            return false;

        Double maxValue = getMaxValue();
        if (maxValue != null && value > maxValue)
            return false;

        return true;
    }

}
