/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

/**
 * Indicates that an object has virtual columns in Ontology Manager
 *
 * User: jgarms
 * Date: Jul 23, 2008
 * Time: 10:59:24 AM
 */
public abstract class ExtensibleStudyEntity<E> extends AbstractStudyEntity<E>
{
    public interface DomainInfo
    {
        String getDomainURI(Container c);
        String getDomainPrefix();
        String getDomainName();
    }

    public static class StudyDomainInfo implements DomainInfo
    {
        final boolean _useSharedProjectDomain;
        final String _domainUriPrefix;

        protected StudyDomainInfo(String domainUriPrefix, boolean useSharedDomain)
        {
            _domainUriPrefix = domainUriPrefix;
            _useSharedProjectDomain = useSharedDomain;
        }

        public String getDomainPrefix()
        {
            return _domainUriPrefix;
        }

        public String getDomainURI(final Container c)
        {
            Container p = c.getProject();
            if (_useSharedProjectDomain && null != p)
            {
                return new Lsid(getDomainPrefix(), "Project-" + p.getRowId(), getDomainPrefix()).toString();
            }
            else
            {
                return new Lsid(getDomainPrefix(), "Folder-" + c.getRowId(), getDomainPrefix()).toString();
            }
        }

        public String getDomainName()
        {
            // for now, the prefix and name happen to be the same
            return _domainUriPrefix;
        }
    }

    public ExtensibleStudyEntity()
    {
        super();
    }

    public ExtensibleStudyEntity(Container c)
    {
        super(c);
    }

    /**
     * Returns a domain URI for use by Ontology Manager
     */
    public DomainInfo getDomainInfo()
    {
        return new StudyDomainInfo(getDomainURIPrefix(), getUseSharedProjectDomain());
    }

    protected abstract String getDomainURIPrefix();
    protected abstract boolean getUseSharedProjectDomain();

    /**
     * Creates and lsid for this individual extensible object
     */
    public abstract void initLsid();

    public abstract String getLsid();

    // This is not up to current standards... but at least we're using the domain to get the PropertyURIs now...
    public void savePropertyBag(Map<String, Object> props) throws SQLException, ValidationException
    {
        Container container = getContainer();
        String ownerLsid = getLsid();
        String domainURI = getDomainInfo().getDomainURI(container);
        Domain domain = PropertyService.get().getDomain(container, domainURI);

        // No domain means no properties are defined
        if (null == domain)
            return;

        Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, ownerLsid);
        if (resourceProperties != null && !resourceProperties.isEmpty())
        {
            OntologyManager.deleteOntologyObject(ownerLsid, container, false);
        }

        List<ObjectProperty> objectProperties = new ArrayList<>(props.size());
        Map<String, DomainProperty> importMap = ImportAliasable.Helper.createImportMap(domain.getNonBaseProperties(), false);

        for (Map.Entry<String, Object> entry : props.entrySet())
        {
            DomainProperty property = importMap.get(entry.getKey());

            if (null != property)
            {
                String propertyURI = property.getPropertyURI();

                if (entry.getValue() != null)
                    objectProperties.add(new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue()));
                else
                    objectProperties.add(new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue(), PropertyType.STRING));
            }
        }

        if (!objectProperties.isEmpty())
            OntologyManager.insertProperties(container, ownerLsid, objectProperties.toArray(new ObjectProperty[objectProperties.size()]));
    }
}
