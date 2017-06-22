/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.experiment.defaults;

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * User: brittp
 * Date: Jan 30, 2009
 * Time: 11:11:14 AM
 */

public class DefaultValueServiceImpl implements DefaultValueService
{
    private static final String DOMAIN_DEFAULT_VALUE_LSID_PREFIX = "DomainDefaultValue";
    private static final String USER_DEFAULT_VALUE_LSID_PREFIX = "UserDefaultValue";
    private static final String USER_DEFAULT_VALUE_DOMAIN_PARENT = "UserDefaultValueParent";

    private final Lock _lock = new ReentrantLock();

    private String getContainerDefaultsLSID(Container container, Domain domain)
    {
        String suffix = "Folder-" + container.getRowId();
        return (new Lsid(DOMAIN_DEFAULT_VALUE_LSID_PREFIX, suffix, domain.getName())).toString();
    }

    private String getUserDefaultsParentLSID(Container container, User user, Domain domain)
    {
        String suffix = "Folder-" + container.getRowId() + ".User-" + user.getUserId();
        return (new Lsid(USER_DEFAULT_VALUE_DOMAIN_PARENT, suffix, domain.getName())).toString();
    }

    private static final String WILD_CARD_PLACEHOLDER = "WILDCARD";

    private String getUserDefaultsWildcardLSID(Container container, Domain domain, boolean parentObject)
    {
        String suffix = "Folder-" + container.getRowId() + ".User-" + WILD_CARD_PLACEHOLDER + (!parentObject ? "." + WILD_CARD_PLACEHOLDER : "");
        String objectId = domain.getName();
        String lsid = (new Lsid(USER_DEFAULT_VALUE_LSID_PREFIX, suffix, objectId)).toString();
        // this hack is to include '%' characters in an LSID-like string.  The '%' character can't be part of the
        // lsid components passed to the Lsid constructor, or it will be encoded as '%25'.
        return lsid.replaceAll(WILD_CARD_PLACEHOLDER, "%");
    }

    private String getUserDefaultsLSID(Container container, User user, Domain domain, String scope)
    {
        String suffix = "Folder-" + container.getRowId() + ".User-" + user.getUserId();
        String objectId = domain.getName();
        if (scope != null)
            objectId += "." + scope;
        return (new Lsid(USER_DEFAULT_VALUE_LSID_PREFIX, suffix, objectId)).toString();
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user) throws ExperimentException
    {
        setDefaultValues(container, values, user, null);
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user, @Nullable String scope) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        // DomainProperty has a hashCode() based on its propertyId. If they were added to the map before they were
        // saved to the database, they'll be in the bucket for values with hashCode() 0 (their uninserted propertyId)
        // so we won't look them up correctly. Recreate the map to rebucket them appropriately.
        values = new HashMap<>(values);

        assert getDomainCount(values) == 1 : "Default values must be saved one domain at a time.";
        Domain domain = values.keySet().iterator().next().getDomain();

        // we create a parent object for this domain; this allows us to delete all instances later, even if there are
        // multiple scopes under the parent.
        String parentLSID = getUserDefaultsParentLSID(container, user, domain);
        OntologyManager.ensureObject(container, parentLSID);
        String objectLSID = getUserDefaultsLSID(container, user, domain, scope);
        replaceObject(container, domain, objectLSID, parentLSID, values);
    }

    public void setDefaultValues(Container container, Map<DomainProperty, Object> values) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        // DomainProperty has a hashCode() based on its propertyId. If they were added to the map before they were
        // saved to the database, they'll be in the bucket for values with hashCode() 0 (their uninserted propertyId)
        // so we won't look them up correctly. Recreate the map to rebucket them appropriately.
        values = new HashMap<>(values);

        assert getDomainCount(values) == 1 : "Default values must be saved one domain at a time.";
        Domain domain = values.keySet().iterator().next().getDomain();
        // first, we validate the post:
        String objectLSID = getContainerDefaultsLSID(container, domain);
        replaceObject(container, domain, objectLSID, null, values);
    }

    private void replaceObject(Container container, Domain domain, String objectLSID, @Nullable String parentLSID, Map<DomainProperty, Object> values) throws ExperimentException
    {
        try (DbScope.Transaction t = OntologyManager.getExpSchema().getScope().ensureTransaction(_lock))
        {
            OntologyManager.deleteOntologyObject(objectLSID, container, true);
            OntologyManager.ensureObject(container, objectLSID, parentLSID);
            List<ObjectProperty> objectProperties = new ArrayList<>();

            for (DomainProperty property : domain.getProperties())
            {
                Object value = values.get(property);
                // Leave it out if it's null, which will prevent it from failing validators
                if (value != null)
                {
                    try
                    {
                        ObjectProperty prop = new ObjectProperty(objectLSID, container, property.getPropertyURI(), value,
                                property.getPropertyDescriptor().getPropertyType(), property.getName());
                        objectProperties.add(prop);
                    }
                    catch (ConversionException e)
                    {
                        Logger.getLogger(DefaultValueServiceImpl.class).warn("Unable to convert default value '" + value + "' for property " + property.getName() + ", dropping it");
                    }
                }
            }
            OntologyManager.insertProperties(container, objectLSID, objectProperties.toArray(new ObjectProperty[objectProperties.size()]));
            t.commit();
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e);
        }
    }

    private Map<DomainProperty, Object> getObjectValues(Container container, Domain domain, String objectLSID)
    {
        Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container, objectLSID);
        Map<String, DomainProperty> propertyURIToProperty = new HashMap<>();
        for (DomainProperty dp : domain.getProperties())
            propertyURIToProperty.put(dp.getPropertyDescriptor().getPropertyURI(), dp);

        Map<DomainProperty, Object> values = new HashMap<>();
        for (Map.Entry<String, ObjectProperty> entry : properties.entrySet())
        {
            DomainProperty property = propertyURIToProperty.get(entry.getValue().getPropertyURI());
            // We won't find the domain property if it has been removed (via user edit) since we last saved default values
            if (property != null)
            {
                Object value = entry.getValue().value();
                if (value != null)
                    values.put(property, value);
            }
        }
        return values;
    }

    public Map<DomainProperty, Object> getMergedValues(Domain domain, Map<DomainProperty, Object> userValues, Map<DomainProperty, Object> globalValues)
    {
        if (userValues == null || userValues.isEmpty())
            return globalValues != null ? globalValues : Collections.emptyMap();

        Map<DomainProperty, Object> result = new HashMap<>();
        for (DomainProperty property : domain.getProperties())
        {
            if (property.getDefaultValueTypeEnum() == DefaultValueType.LAST_ENTERED && userValues.containsKey(property))
                result.put(property, userValues.get(property));
            else
            {
                Object value = globalValues.get(property);
                if (value != null)
                    result.put(property, value);
            }
        }
        return result;
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user, @Nullable String scope)
    {
        Map<DomainProperty, Object> userValues = null;
        Container checkContainer = container;
        // If we've already checked for default values in the domain's container, we don't need to look elsewhere,
        // since it won't be referenced from any parent containers 
        boolean checkedDomainContainer = false;
        if (user != null)
        {
            while (!checkedDomainContainer && checkContainer != null && !checkContainer.isRoot() && (userValues == null || userValues.isEmpty()))
            {
                String userDefaultLSID = getUserDefaultsLSID(checkContainer, user, domain, scope);
                userValues = getObjectValues(checkContainer, domain, userDefaultLSID);
                if (checkContainer.equals(domain.getContainer()))
                {
                    // Stop looking if we've gotten to the domain's container. Normal scoping won't have parent
                    // containers use the domain of a child container
                    checkedDomainContainer = true;
                }
                checkContainer = checkContainer.getParent();
            }
            if (!checkedDomainContainer && (userValues == null || userValues.isEmpty()))
            {
                // Also check in the domain's home container since it wasn't part of the chain to the root. It's likely
                // in the /Shared project, but we'll check wherever the domain lives
                String userDefaultLSID = getUserDefaultsLSID(domain.getContainer(), user, domain, scope);
                userValues = getObjectValues(domain.getContainer(), domain, userDefaultLSID);
            }
        }

        Map<DomainProperty, Object> globalValues = null;
        checkContainer = container;
        checkedDomainContainer = false;
        while (!checkedDomainContainer && checkContainer != null && !checkContainer.isRoot() && (globalValues == null || globalValues.isEmpty()))
        {
            String globalDefaultLSID = getContainerDefaultsLSID(checkContainer, domain);
            globalValues = getObjectValues(checkContainer, domain, globalDefaultLSID);
            if (checkContainer.equals(domain.getContainer()))
            {
                // Stop looking if we've gotten to the domain's container. Normal scoping won't have parent
                // containers use the domain of a child container
                checkedDomainContainer = true;
            }
            checkContainer = checkContainer.getParent();
        }
        if (!checkedDomainContainer && (globalValues == null || globalValues.isEmpty()))
        {
            // Also check in the domain's home container since it wasn't part of the chain to the root. It's likely
            // in the /Shared project, but we'll check wherever the domain lives
            String globalDefaultLSID = getContainerDefaultsLSID(domain.getContainer(), domain);
            globalValues = getObjectValues(domain.getContainer(), domain, globalDefaultLSID);
        }
        return getMergedValues(domain, userValues, globalValues);
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user)
    {
        return getDefaultValues(container, domain, user, null);
    }

    public Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain)
    {
        return getDefaultValues(container, domain, null, null);
    }

    private void clearDefaultValues(Container container, String lsid)
    {
        OntologyManager.deleteOntologyObject(lsid, container, true);
    }

    public void clearDefaultValues(Container container, Domain domain)
    {
        clearDefaultValues(container, getContainerDefaultsLSID(container, domain));
        // get two expressions to delete all user-based defaults in this container:
        String userParentLsid = getUserDefaultsWildcardLSID(container, domain, true);
        String userScopesLsid = getUserDefaultsWildcardLSID(container, domain, false);
        StringBuilder sql = new StringBuilder("SELECT ObjectURI FROM " + OntologyManager.getTinfoObject() + " WHERE ObjectURI LIKE ?");
        OntologyManager.deleteOntologyObjects(ExperimentService.get().getSchema(), new SQLFragment(sql, userParentLsid), container, false);
        OntologyManager.deleteOntologyObjects(ExperimentService.get().getSchema(), new SQLFragment(sql, userScopesLsid), container, false);
    }

    public void clearDefaultValues(Container container, Domain domain, User user)
    {
        clearDefaultValues(container, getUserDefaultsParentLSID(container, user, domain));
    }

    public void clearDefaultValues(Container container, Domain domain, User user, String scope)
    {
        clearDefaultValues(container, getUserDefaultsLSID(container, user, domain, scope));
    }

    protected int getDomainCount(Map<DomainProperty, Object> values)
    {
        Set<Domain> domains = new HashSet<>();
        for (DomainProperty prop : values.keySet())
            domains.add(prop.getDomain());
        return domains.size();
    }

    private void getDefaultValueOverriders(Container currentContainer, Domain domain, List<Container> overriders)
    {
        for (Container child : currentContainer.getChildren())
        {
            String lsid = getContainerDefaultsLSID(child, domain);
            if (OntologyManager.checkObjectExistence(lsid))
                overriders.add(child);
            getDefaultValueOverriders(child, domain, overriders);
        }
    }

    public List<Container> getDefaultValueOverriders(Container currentContainer, Domain domain)
    {
        List<Container> overriders = new ArrayList<>();
        getDefaultValueOverriders(currentContainer, domain, overriders);
        return overriders;
    }

    private void getDefaultValueOverridees(Container currentContainer, Domain domain, List<Container> overridees)
    {
        if (currentContainer.isRoot())
            return;
        getDefaultValueOverridees(currentContainer.getParent(), domain, overridees);
        String lsid = getContainerDefaultsLSID(currentContainer, domain);
        if (OntologyManager.checkObjectExistence(lsid))
            overridees.add(currentContainer);
    }

    public List<Container> getDefaultValueOverridees(Container currentContainer, Domain domain)
    {
        List<Container> overridees = new ArrayList<>();
        if (!currentContainer.isRoot())
            getDefaultValueOverridees(currentContainer.getParent(), domain, overridees);
        return overridees;
    }

    public boolean hasDefaultValues(Container container, Domain domain, boolean inherit)
    {
        Container current = container;
        while ((current == container || inherit) && !current.isRoot())
        {
            String lsid = getContainerDefaultsLSID(current, domain);
            if (OntologyManager.checkObjectExistence(lsid))
                return true;
            current = current.getParent();
        }
        return false;
    }

    public boolean hasDefaultValues(Container container, Domain domain, User user, boolean inherit)
    {
        return hasDefaultValues(container, domain, user, null, inherit);
    }

    public boolean hasDefaultValues(Container container, Domain domain, User user, String scope, boolean inherit)
    {
        Container current = container;
        while ((current == container || inherit) && !current.isRoot())
        {
            String lsid = getUserDefaultsLSID(container, user, domain, scope);
            if (OntologyManager.checkObjectExistence(lsid))
                return true;
            current = current.getParent();
        }
        return false;
    }
}