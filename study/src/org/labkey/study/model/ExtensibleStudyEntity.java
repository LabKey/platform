/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
import org.labkey.api.exp.*;
import org.labkey.api.query.ValidationException;

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

    public void savePropertyBag(Map<String, Object> props) throws SQLException, ValidationException
    {
        Container container = getContainer();
        String ownerLsid = getLsid();
        String classLsid = getDomainInfo().getDomainURI(container);

        Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, ownerLsid);
        if (resourceProperties != null && !resourceProperties.isEmpty())
        {
            OntologyManager.deleteOntologyObject(ownerLsid, container, false);
        }
        ObjectProperty[] objectProperties = new ObjectProperty[props.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : props.entrySet())
        {
            String propertyURI = Lsid.isLsid(entry.getKey()) ? entry.getKey() : classLsid + "#" + entry.getKey();
            if (entry.getValue() != null)
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue());
            else
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue(), PropertyType.STRING);
        }
        if (objectProperties.length > 0)
            OntologyManager.insertProperties(container, ownerLsid, objectProperties);
    }
}
