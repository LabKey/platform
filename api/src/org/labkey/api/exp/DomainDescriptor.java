/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.data.Container;

import java.util.Map;


/**
 * User: phussey
 * Date: Apr 18, 2006
 * Time: 11:38:27 AM
 */
public class DomainDescriptor implements Cloneable 
{
    private Object _ts;     // for optimistic concurrency
    private int domainId;
    private String name;
    private String domainURI;
    private String description;
    private Container container;
    private Container project;
    private int titlePropertyId;

    // for StorageProvisioner (currently assuming labkey scope)
    private String storageTableName;
    private String storageSchemaName;

    public DomainDescriptor()
    {
        MemTracker.getInstance().put(this);
    }

    public DomainDescriptor(String domainURI, Container c)
    {
        this();
        setDomainURI(domainURI);
        setContainer(c);
    }

    public Object get_Ts()
    {
        return _ts;
    }

    public void set_Ts(Object ts)
    {
        _ts = ts;
    }

    public int getDomainId()
    {
        return domainId;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDomainURI()
    {
        return domainURI;
    }

    public void setDomainURI(String domainURI)
    {
        this.domainURI = domainURI;
        if (null == name && null != domainURI)
        {
            int pos;
            pos = domainURI.lastIndexOf("#");
            if (pos < 0)
                pos = domainURI.lastIndexOf(":");

            if (pos >= 0)
                name = PageFlowUtil.decode(domainURI.substring(pos + 1));
        }

    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Container getContainer()
    {
        return container;
    }

    public void setContainer(Container container)
    {
 //       if (container.equals(ContainerManager.getRoot()))
 //            container=ContainerManager.getSharedContainer();

        this.container = container;
        if (null==project)
            this.project = container.getProject();
        if (null==project)
             project=container;
     }

    public Container getProject() {
        return project;
    }

    public void setProject(Container project) {
        this.project = project;
    }


    public int getTitlePropertyId()
    {
        return titlePropertyId;
    }

    public void setTitlePropertyId(int titlePropertyId)
    {
        this.titlePropertyId = titlePropertyId;
    }


    @Nullable   // null if not provisioned
    public String getStorageTableName()
    {
        return storageTableName;
    }


    public void setStorageTableName(String storageTableName)
    {
        this.storageTableName = storageTableName;
    }


    public String getStorageSchemaName()
    {
        return storageSchemaName;
    }


    public void setStorageSchemaName(String storageSchemaName)
    {
        this.storageSchemaName = storageSchemaName;
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
}
