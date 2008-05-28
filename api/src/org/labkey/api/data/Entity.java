/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;

import javax.ejb.Column;
import javax.ejb.Transient;
import java.util.Date;


/**
 * User: mbellew
 * Date: Feb 16, 2005
 * Time: 11:34:34 AM
 */
public class Entity implements java.io.Serializable, Ownable
{
    protected String entityId;
    private int createdBy;
    private long created = 0;
    private int modifiedBy;
    private long modified;
    private String containerId;


    protected void copyTo(Entity to)
    {
        to.entityId = entityId;
        to.createdBy = createdBy;
        to.created = created;
        to.modifiedBy = modifiedBy;
        to.modified = modified;
        to.containerId = containerId;
    }


    public void beforeSave(User user, String containerId)
    {
        if (created == 0)
            beforeInsert(user, containerId);
        else
            beforeUpdate(user);
    }


    public void beforeUpdate(User user)
    {
        if (user != null)
            modifiedBy = user.getUserId();
        modified = System.currentTimeMillis();
    }


    public void beforeUpdate(User user, Entity cur)
    {
        entityId = cur.entityId;
        containerId = cur.containerId;
        createdBy = cur.createdBy;
        created = cur.created;

        beforeUpdate(user);
    }

    public void beforeInsert(User user, String containerId)
    {
        if (null == entityId)
            entityId = GUID.makeGUID();
        this.containerId = containerId;
        if (user != null)
            createdBy = user.getUserId();
        created = System.currentTimeMillis();
        modifiedBy = createdBy;
        modified = created;
    }


    protected Entity()
    {
        assert MemTracker.put(this);
    }


    public int getCreatedBy()
    {
        return createdBy;
    }


    public void setCreatedBy(int createdBy)
    {
        this.createdBy = createdBy;
    }


    public Date getCreated()
    {
        return new Date(created);
    }


    public void setCreated(Date created)
    {
        this.created = created.getTime();
    }


    public int getModifiedBy()
    {
        return modifiedBy;
    }


    public void setModifiedBy(int modifiedBy)
    {
        this.modifiedBy = modifiedBy;
    }


    public Date getModified()
    {
        return new Date(modified);
    }


    public void setModified(Date modified)
    {
        this.modified = modified.getTime();
    }


    @Column(name = "Container")
    public String getContainerId()
    {
        return containerId;
    }


    @Column(nullable = false)
    public void setContainerId(String containerId)
    {
        this.containerId = containerId;
    }


    // for Table layer
    public void setContainer(String containerId)
    {
        this.containerId = containerId;
    }

    public String getEntityId()
    {
        return entityId;
    }

    public void setEntityId(String entityId)
    {
        if (this.entityId != null && !this.entityId.equals(entityId))
            throw new IllegalStateException("can't change entityid");
        this.entityId = entityId;
    }

    public Container lookupContainer()
    {
        if (null != containerId)
        {
            return ContainerManager.getForId(containerId);
        }
        return null;
    }

    @Transient
    public String getContainerPath()
    {
        if (null != containerId)
        {
            Container c = ContainerManager.getForId(containerId);
            if (null != c)
                return c.getPath();
        }
        return "";
    }
}
