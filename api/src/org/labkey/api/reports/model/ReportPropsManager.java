/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.api.reports.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.data.xml.reportProps.PropDefDocument;
import org.labkey.data.xml.reportProps.PropValueDocument;
import org.labkey.data.xml.reportProps.PropertyDocument;
import org.labkey.data.xml.reportProps.PropertyList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Feb 13, 2012
 */
public class ReportPropsManager extends ContainerManager.AbstractContainerListener
{
    private static final Logger _log = Logger.getLogger(ReportPropsManager.class);
    private static final String PROPERTIES_DOMAIN = "Report Properties";
    private static final String NAMESPACE_PREFIX = "ReportProperties";
    private static final String TYPE_PROPERTIES = "Properties";

    private static ReportPropsManager _instance = new ReportPropsManager();

    private ReportPropsManager()
    {
        ContainerManager.addContainerListener(this);
    }

    public static ReportPropsManager get()
    {
        return _instance;
    }

    public List<DomainProperty> getProperties(Container container)
    {
        List<DomainProperty> properties = new ArrayList<>();
        Domain domain = getDomain(container);

        if (domain != null)
        {
            properties.addAll(domain.getProperties());
        }
        return properties;
    }

    public void createProperty(Container container, User user, String name, String label, PropertyType type) throws Exception
    {
        ensureProperty(container, user, name, label, type);
    }

    private Map<String, DomainProperty> getPropertyMap(Container container)
    {
        Domain domain = getDomain(container);
        Map<String, DomainProperty> propsMap = new HashMap<>();

        if (domain != null)
        {
            for (DomainProperty dp : domain.getProperties())
                propsMap.put(dp.getName(), dp);
        }
        return propsMap;
    }

    public synchronized DomainProperty ensureProperty(Container container, User user, String name, String label, PropertyType type) throws ChangePropertyDescriptorException
    {
        Domain domain = getDomain(container);
        if (domain != null)
        {
            boolean dirty = false;
            Map<String, DomainProperty> existingProps = getPropertyMap(container);
            DomainProperty dp = existingProps.get(name);

            if (dp == null)
            {
                dirty = true;

                DomainProperty prop = domain.addProperty();
                prop.setName(name);
                prop.setLabel(label);
                prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
                prop.setPropertyURI(getPropertyURI(name, container));

                dp = prop;
            }

            if (dirty)
                domain.save(user);

            return dp;
        }
        return null;
    }

    public synchronized void setPropertyValue(String entityId, Container container, String propertyName, Object value) throws ValidationException
    {
        if (entityId == null || container == null)
            return;

        DbScope scope = CoreSchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Map<String, DomainProperty> propMap = getPropertyMap(container);

            if (propMap.containsKey(propertyName))
            {
                DomainProperty prop = propMap.get(propertyName);
                String rowLsid = makeLsid(entityId);

                OntologyManager.deleteProperty(rowLsid, prop.getPropertyURI(), container, container);

                if (value != null)
                {
                    ObjectProperty oProp = new ObjectProperty(rowLsid, container, prop.getPropertyURI(), value, prop.getPropertyDescriptor().getPropertyType());
                    oProp.setPropertyId(prop.getPropertyId());
                    OntologyManager.insertProperties(container, rowLsid, oProp);
                }
            }
            transaction.commit();
        }
    }

    private String makeLsid(String entityId)
    {
        return "urn:uuid:" + entityId;
    }

    public Object getPropertyValue(String entityId, Container container, String propName)
    {
        Map<String, DomainProperty> propMap = getPropertyMap(container);

        if (propMap.containsKey(propName))
        {
            String rowLsid = makeLsid(entityId);
            DomainProperty prop = propMap.get(propName);
            Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(container, rowLsid);
            if (props.containsKey(prop.getPropertyURI()))
            {
                return props.get(prop.getPropertyURI()).getObjectValue();
            }
        }
        return null;
    }

    @Nullable
    private Domain getDomain(Container container)
    {
        String uri = getDomainURI(container);
        try
        {
            DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(uri, PROPERTIES_DOMAIN, container);
            return PropertyService.get().getDomain(dd.getDomainId());
        }
        catch (RuntimeSQLException e)
        {
            if (ContainerManager.getForRowId(container.getRowId()) == null)
            {
                // Hack to avoid treating this as a real error if the container has been deleted already. See issue 22788
                throw new NotFoundException("Container " + container.getPath() + " has been deleted");
            }
            throw e;
        }
    }

    private String getDomainURI(@NotNull Container container)
    {
        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE_PREFIX + ".Folder-" + container.getRowId() + ':' + TYPE_PROPERTIES).toString();
    }

    private String getPropertyURI(String propertyName, Container container)
    {
        return getDomainURI(container) + '#' + propertyName;
    }

    @Override
    public void containerDeleted(Container container, User user)
    {
        String uri = getDomainURI(container);
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(uri, container);

        if (dd != null)
        {
            Domain domain = PropertyService.get().getDomain(dd.getDomainId());
            if (domain != null)
            {
                try
                {
                    domain.delete(user);
                }
                catch (DomainNotFoundException ignored)
                {
                    // OK, it's already gone
                }
            }
        }
    }

    public List<Pair<DomainProperty, Object>> getProperties(String entityId, Container container)
    {
        List<Pair<DomainProperty, Object>> properties = new ArrayList<>();

        for (DomainProperty dp : getProperties(container))
        {
            Object value = getPropertyValue(entityId, container, dp.getName());

            if (value != null)
                properties.add(new Pair<>(dp, value));
        }
        return properties;
    }

    public void exportProperties(String entityId, Container container, PropertyList propertyList)
    {
        if (propertyList == null)
            throw new IllegalArgumentException("PropertyList cannot be null");

        try {
            for (Pair<DomainProperty, Object> tag : getProperties(entityId, container))
            {
                PropertyDocument.Property prop = propertyList.addNewProperty();

                PropDefDocument.PropDef propDef = prop.addNewPropDef();

                propDef.setName(tag.getKey().getName());
                propDef.setLabel(tag.getKey().getLabel());
                propDef.setType(tag.getKey().getPropertyDescriptor().getPropertyType().getTypeUri());

                PropValueDocument.PropValue propValue = prop.addNewPropValue();
                propValue.setValue(String.valueOf(tag.getValue()));
            }
        }
        catch (Exception e)
        {
            _log.error("Error occured serializing report properties.", e);
        }
    }

    public void importProperties(String entityId, Container container, User user, PropertyList propertyList)
    {
        if (propertyList == null)
            throw new IllegalArgumentException("PropertyList cannot be null");

        if (entityId == null)
            throw new IllegalArgumentException("EntityId cannot be null");

        for (PropertyDocument.Property prop : propertyList.getPropertyArray())
        {
            try {
                PropDefDocument.PropDef propDef = prop.getPropDef();

                PropertyType type = PropertyType.getFromURI(propDef.getType(), propDef.getType());
                DomainProperty dp = ensureProperty(container, user, propDef.getName(), propDef.getLabel(), type);

                PropValueDocument.PropValue propValue = prop.getPropValue();
                setPropertyValue(entityId, container, dp.getName(), propValue.getValue());
            }
            catch (Exception e)
            {
                _log.error("Error occured importing report properties.", e);
            }
        }
    }
}
