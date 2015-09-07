/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;

import java.util.Date;


/**
 * User: mbellew
 * Date: Feb 16, 2005
 * Time: 11:34:34 AM
 */
public class Entity implements java.io.Serializable, Ownable
{
    protected GUID entityId;
    private int createdBy;
    private long created = 0;
    private int modifiedBy;
    private long modified;
    private GUID containerId;


    protected void copyTo(Entity to)
    {
        to.entityId = entityId;
        to.createdBy = createdBy;
        to.created = created;
        to.modifiedBy = modifiedBy;
        to.modified = modified;
        to.containerId = containerId;
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
            entityId = new GUID();
        this.containerId = new GUID(containerId);
        if (user != null)
            createdBy = user.getUserId();
        created = System.currentTimeMillis();
        modifiedBy = createdBy;
        modified = created;
    }


    protected Entity()
    {
        MemTracker.getInstance().put(this);
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


    public String getContainerId()
    {
        return null==containerId?null:containerId.toString();
    }


    public void setContainerId(String containerId)
    {
        this.containerId = containerId == null ? null : new GUID(containerId);
    }


    // for Table layer
    public void setContainer(String containerId)
    {
        setContainerId(containerId);
    }

    @Nullable
    public String getEntityId()
    {
        return null==entityId ? null : entityId.toString();
    }

    public void setEntityId(String entityIdStr)
    {
        // TODO: Check with Matt -- allow new GUID(null) instead?  Or disallow null?
        if (null == entityIdStr)
        {
            this.entityId = null;
        }
        else
        {
            GUID entityId = new GUID(entityIdStr);
            if (this.entityId != null && !this.entityId.equals(entityId))
                throw new IllegalStateException("can't change entityid");
            this.entityId = entityId;
        }
    }

    @Nullable
    public Container lookupContainer()
    {
        if (null != containerId)
        {
            return ContainerManager.getForId(containerId);
        }
        return null;
    }

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
