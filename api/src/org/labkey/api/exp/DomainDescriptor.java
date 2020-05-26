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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BuilderObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;

import java.util.Map;
import java.util.Objects;

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
    private final int _domainId;
    private final String name;
    private final String _domainURI;
    private final String _description;
    private final Container _container;
    private final Container _project;
    private final int _titlePropertyId;
    private final TemplateInfo _templateInfo;

    /* DomainDescriptors are cached, but DomainImpl is not, so cache DomainKind here on DomainDescriptor */
    private DomainKind<?> _domainKind;

    // for StorageProvisioner (currently assuming labkey scope)
    private final String _storageTableName;
    private final String _storageSchemaName;

    private DomainDescriptor(
            String domainURI, Container c, Container p, String name,
            int domainId, String description, String storageTableName, String storageSchemaName,
            int titlePropertyId, Object ts,
            @Nullable TemplateInfo templateInfo
    )
    {
        MemTracker.getInstance().put(this);
        _ts = ts;
        _templateInfo = templateInfo;
        _description = description;
        _domainURI = domainURI;
        _domainId = domainId;

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

        _container = c;

        if (null != p)
        {
            _project = p;
        }
        else if (null != _container)
        {
            // root container would return a null for project
            _project = _container.getProject() != null ? _container.getProject() : _container;
        }
        else
        {
            _project = null /* container is null */;
        }

        _storageTableName = storageTableName;
        _storageSchemaName = storageSchemaName;
        _titlePropertyId = titlePropertyId;
    }

    /**
     * This is intended only for use by the MapConstructorObjectFactory. Use DomainDescriptor.Builder
     * to construct new Domain Descriptors.
     */
    public DomainDescriptor(Map<String, Object> map)
    {
        _ts = map.get("_ts");

        _domainURI = (String) map.get("DomainURI");

        if (map.containsKey("DomainId"))
            _domainId = (Integer) map.get("DomainId");
        else
            _domainId = 0;

        name = (String) map.get("Name");
        _container = ContainerManager.getForId((String) map.get("Container"));
        _project = ContainerManager.getForId((String) map.get("Project"));
        _description = (String) map.get("Description");
        _storageSchemaName = (String) map.get("StorageSchemaName");
        _storageTableName = (String) map.get("StorageTableName");

        // This property is not stored in the database
        if (map.containsKey("titlePropertyId"))
            _titlePropertyId = (Integer) map.get("titlePropertyId");
        else
            _titlePropertyId = 0;

        _templateInfo = null;
    }

    public synchronized DomainKind<?> getDomainKind()
    {
        if (null == _domainKind)
            _domainKind = PropertyService.get().getDomainKind(_domainURI);
        return _domainKind;
    }

    public DomainDescriptor.Builder edit()
    {
        return new Builder(this);
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getDescription()
    {
        return _description;
    }

    public int getDomainId()
    {
        return _domainId;
    }

    public String getName()
    {
        return name;
    }

    public String getDomainURI()
    {
        return _domainURI;
    }

    public Container getProject()
    {
        return _project;
    }

    public int getTitlePropertyId()
    {
        return _titlePropertyId;
    }

    @Nullable   // null if not provisioned
    public String getStorageTableName()
    {
        return _storageTableName;
    }

    public String getStorageSchemaName()
    {
        return _storageSchemaName;
    }

    @Nullable
    public TemplateInfo getTemplateInfo()
    {
        return _templateInfo;
    }

    public Object get_Ts()
    {
        return _ts;
    }

    @Override
    public String toString()
    {
        return _domainURI + " name=" + name + " project=" + (_project == null ? "null" : _project.getPath()) + " container=" + (_container == null ? "null" : _container.getPath());
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
        return Integer.valueOf(getDomainId()).hashCode();
    }

    /** Compares just the IDs */
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

    /** Compare all persisted properties */
    public boolean deepEquals(DomainDescriptor d)
    {
        return (d.getDomainId() == 0 || Objects.equals(getDomainId(), d.getDomainId())) &&
                Objects.equals(getName(), d.getName()) &&
                Objects.equals(getStorageTableName(), d.getStorageTableName()) &&
                Objects.equals(getStorageSchemaName(), d.getStorageSchemaName()) &&
                Objects.equals(getProject(), d.getProject()) &&
                Objects.equals(getTitlePropertyId(), d.getTitlePropertyId()) &&
                Objects.equals(getDomainURI(), d.getDomainURI()) &&
                Objects.equals(getDescription(), d.getDescription()) &&
                Objects.equals(getContainer(), d.getContainer());

    }

    public boolean isProvisioned()
    {
        return _storageSchemaName != null && _storageTableName != null;
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
            _ts = ts;
            return this;
        }

        public Builder set_Ts(Object ts)
        {
            _ts = ts;
            return this;
        }
    }
}
