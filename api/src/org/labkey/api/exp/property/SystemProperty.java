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

package org.labkey.api.exp.property;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.util.MemTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SystemProperty
{
    private static final Logger _log = LogManager.getLogger(SystemProperty.class);
    private static final Map<String, SystemProperty> _systemProperties = new LinkedHashMap<>();

    private static boolean _registered = false;

    private final String _propertyURI;
    private final PropertyType _type;
    private final String _name;

    private PropertyDescriptor _pd;

    public SystemProperty(String propertyURI, PropertyType type, String name)
    {
        if (_registered)
            throw new IllegalStateException("System properties can only be registered at startup");
        if (_systemProperties.containsKey(propertyURI))
            throw new IllegalArgumentException("System property " + propertyURI + " is already registered.");
        _systemProperties.put(propertyURI, this);
        _propertyURI = propertyURI;
        _type = type;
        _name = name;
    }

    public SystemProperty(String propertyURI, PropertyType type)
    {
        this(propertyURI, type, null);
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        if (_pd == null)
            throw new IllegalStateException("System property can't be used until registered");
        return _pd;
    }

    private void register() throws ChangePropertyDescriptorException
    {
        _pd = OntologyManager.getPropertyDescriptor(_propertyURI, getContainer());
        MemTracker.getInstance().remove(_pd);  // these are globals now, so don't track
        PropertyDescriptor pd = constructPropertyDescriptor();
        if (_pd == null)
        {
            _pd = OntologyManager.insertPropertyDescriptor(pd);
        }
        else
        {
            // ensure the PropertyDescriptor is up to date
            pd.setPropertyId(_pd.getPropertyId());
            _pd = OntologyManager.updatePropertyDescriptor(pd);
        }
        MemTracker.getInstance().remove(_pd);
    }

    protected PropertyDescriptor constructPropertyDescriptor()
    {
        return new PropertyDescriptor(_propertyURI, _type, _name, getContainer());
    }

    static public void registerProperties()
    {
        if (_registered)
            throw new IllegalStateException("System properties have already been registered");
        for (SystemProperty property : _systemProperties.values())
        {
            try
            {
                property.register();
            }
            catch (Exception e)
            {
                _log.error("Error", e);
            }
        }
        _registered = true;
    }

    static public List<PropertyDescriptor> getProperties()
    {
        if (!_registered)
            throw new IllegalStateException("System properties can only be enumerated after startup");

        ArrayList<PropertyDescriptor> properties = new ArrayList<>(_systemProperties.size());
        for (SystemProperty property : _systemProperties.values())
            properties.add(property._pd);

        properties.sort(Comparator.comparing(ColumnRenderPropertiesImpl::getPropertyURI));
        return Collections.unmodifiableList(properties);
    }

    protected Container getContainer()
    {
        return ContainerManager.getSharedContainer();
    }
}
