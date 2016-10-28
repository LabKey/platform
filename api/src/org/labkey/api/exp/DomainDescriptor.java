/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BuilderObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;

import java.util.Map;

/**
 * Bean class for domains (persisted in exp.DomainDescriptor). Most code shouldn't use this class directly, but should
 * instead use a {@link org.labkey.api.exp.property.Domain} wrapper.
 */
public final class DomainDescriptor
{
    static
    {
        ObjectFactory.Registry.register(DomainDescriptor.class, new BuilderObjectFactory<DomainDescriptor>(DomainDescriptor.class,Builder.class)
        {
            @Override
            protected void fixupMap(Map m, DomainDescriptor dd)
            {
                TemplateInfo ti = dd.getTemplateInfo();
                if (null != ti)
                    m.put("templateInfo",ti.toJSON());
            }
        });
    }

    private final Object _ts;     // for optimistic concurrency
    private final int domainId;
    private final String name;
    private final String domainURI;
    private final String description;
    private final Container container;
    private final Container project;
    private final int titlePropertyId;
    private final TemplateInfo templateInfo;

    // for StorageProvisioner (currently assuming labkey scope)
    private final String storageTableName;
    private final String storageSchemaName;

    private DomainDescriptor(
            String domainURI, Container c, Container p, String name,
            int domainId, String description, String storageTableName, String storageSchemaName,
            int titlePropertyId, Object ts,
            @Nullable TemplateInfo templateInfo
    )
    {
        MemTracker.getInstance().put(this);
        this._ts = ts;
        this.templateInfo = templateInfo;

        this.description = description;

        this.domainURI = domainURI;
        this.domainId = domainId;

        String _name = null;
        if (null != name)
        {
            _name = name;
        }
        else if (null != domainURI)
        {
            int pos = domainURI.lastIndexOf("#");

            if (pos < 0)
                pos = domainURI.lastIndexOf(":");

            if (pos >= 0)
                _name = PageFlowUtil.decode(domainURI.substring(pos + 1));
        }
        this.name = _name;

        this.container = c;

        if (null != p)
        {
            this.project = p;
        }
        else if (null != this.container)
        {
            // root container would return a null for project
            this.project = container.getProject() != null ? container.getProject() : container;
        }
        else
        {
            this.project = null /* container is null */;
        }

        this.storageTableName = storageTableName;
        this.storageSchemaName = storageSchemaName;

        this.titlePropertyId = titlePropertyId;
    }

    /**
     * This is intended only for use by the MapConstructorObjectFactory. Use DomainDescriptor.Builder
     * to construct new Domain Descriptors.
     */
    public DomainDescriptor(Map<String, Object> map)
    {
        _ts = map.get("_ts");

        domainURI = (String) map.get("DomainURI");

        if (map.containsKey("DomainId"))
            domainId = (Integer) map.get("DomainId");
        else
            domainId = 0;

        name = (String) map.get("Name");
        container = ContainerManager.getForId((String) map.get("Container"));
        project = ContainerManager.getForId((String) map.get("Project"));
        description = (String) map.get("Description");
        storageSchemaName = (String) map.get("StorageSchemaName");
        storageTableName = (String) map.get("StorageTableName");

        // This property is not stored in the database
        if (map.containsKey("titlePropertyId"))
            titlePropertyId = (Integer) map.get("titlePropertyId");
        else
            titlePropertyId = 0;

        templateInfo = null;
    }

    public DomainDescriptor.Builder edit()
    {
        return new Builder(this);
    }

    public Container getContainer()
    {
        return container;
    }

    public String getDescription()
    {
        return description;
    }

    public int getDomainId()
    {
        return domainId;
    }

    public String getName()
    {
        return name;
    }

    public String getDomainURI()
    {
        return domainURI;
    }

    public Container getProject()
    {
        return project;
    }

    public int getTitlePropertyId()
    {
        return titlePropertyId;
    }

    @Nullable   // null if not provisioned
    public String getStorageTableName()
    {
        return storageTableName;
    }

    public String getStorageSchemaName()
    {
        return storageSchemaName;
    }

    @Nullable
    public TemplateInfo getTemplateInfo()
    {
        return templateInfo;
    }

    public Object get_Ts()
    {
        return _ts;
    }

    @Override
    public String toString()
    {
        return domainURI + " name=" + name + " project=" + (project == null ? "null" : project.getPath()) + " container=" + (container == null ? "null" : container.getPath());
    }

    public DomainDescriptor clone()
    {
        try
        {
            // templateInfo is immutable, don't need to clone
            return (DomainDescriptor) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }

    @Override
    public int hashCode()
    {
        return new Integer(getDomainId()).hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DomainDescriptor))
            return false;
        // domain descriptors that are not in the database are never equal:
        if (((DomainDescriptor) obj).getDomainId() == 0 || getDomainId() == 0)
            return false;

        // two domain descriptors are equal if they have the same row ID:
        return ((DomainDescriptor) obj).getDomainId() == getDomainId();
    }

    public boolean isProvisioned()
    {
        return this.storageSchemaName != null && this.storageTableName != null;
    }

    public static class Builder implements org.labkey.api.data.Builder<DomainDescriptor>
    {
        private Object _ts;
        private int domainId=0;
        private String name;
        private String domainURI;
        private String description;
        private Container container;
        private Container project;
        private int titlePropertyId=0;
        private String storageTableName;
        private String storageSchemaName;
        private TemplateInfo templateInfo;

        public Builder()
        {

        }

        public Builder(String domainURI, Container container)
        {
            this.domainURI = domainURI;
            this.container = container;
        }

        public Builder(DomainDescriptor dd)
        {
            setTs(dd.get_Ts());

            setContainer(dd.getContainer());
            setProject(dd.getProject());

            setDomainURI(dd.getDomainURI());
            setDomainId(dd.getDomainId());

            setName(dd.getName());
            setDescription(dd.getDescription());
            setTitlePropertyId(dd.getTitlePropertyId());

            setStorageTableName(dd.getStorageTableName());
            setStorageSchemaName(dd.getStorageSchemaName());
            setTemplateInfoObject(dd.getTemplateInfo());
        }

        public DomainDescriptor build()
        {
            return new DomainDescriptor(
                    domainURI, container, project, name,
                    domainId, description, storageTableName, storageSchemaName,
                    titlePropertyId, _ts, templateInfo
            );
        }

        public Builder setDomainId(int domainId)
        {
            this.domainId = domainId;
            return this;
        }

        public Builder setName(String name)
        {
            this.name = name;
            return this;
        }

        public Builder setDomainURI(String domainURI)
        {
            this.domainURI = domainURI;
            return this;
        }

        public Builder setDescription(String description)
        {
            this.description = description;
            return this;
        }

        public Builder setContainer(Container container)
        {
            this.container = container;
            return this;
        }

        public Builder setProject(Container project)
        {
            this.project = project;
            return this;
        }

        public Builder setTitlePropertyId(int titlePropertyId)
        {
            this.titlePropertyId = titlePropertyId;
            return this;
        }

        public Builder setStorageTableName(String storageTableName)
        {
            this.storageTableName = storageTableName;
            return this;
        }

        public Builder setStorageSchemaName(String storageSchemaName)
        {
            this.storageSchemaName = storageSchemaName;
            return this;
        }

        public Builder setTemplateInfo(String json)
        {
            if (StringUtils.isBlank(json))
                this.templateInfo = null;
            else
                this.templateInfo = TemplateInfo.fromJson(json);
            return this;
        }

        public Builder setTemplateInfoObject(TemplateInfo templateInfo)
        {
            this.templateInfo = templateInfo;
            return this;
        }

        public Builder setTs(Object ts)
        {
            this._ts = ts;
            return this;
        }

        public Builder set_Ts(Object ts)
        {
            this._ts = ts;
            return this;
        }
    }
}
