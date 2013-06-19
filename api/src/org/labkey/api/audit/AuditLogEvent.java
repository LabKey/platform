/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;

import java.util.Date;

/**
 * User: Karl Lum
 * Date: Sep 20, 2007
 */
public class AuditLogEvent
{
    private int _rowId;
    private Date _created;
    private User _createdBy;
    private Integer _impersonatedBy;
    private String _entityId;
    private String _comment;
    private String _projectId;
    private String _containerId;
    private String _eventType;
    private String _key1;
    private String _key2;
    private String _key3;
    private Integer _intKey1;
    private Integer _intKey2;
    private Integer _intKey3;
    private String _lsid;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }
    
    public void setCreatedBy(@Nullable User user)
    {
        if (user != null)
        {
            _createdBy = user;

            if (user.isImpersonated())
            {
                User impersonatingUser = user.getImpersonatingUser();
                setImpersonatedBy(impersonatingUser.getUserId());
            }
        }
    }

    public Integer getImpersonatedBy()
    {
        return _impersonatedBy;
    }

    public void setImpersonatedBy(Integer impersonatedBy)
    {
        _impersonatedBy = impersonatedBy;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(String containerId)
    {
        _containerId = containerId;
    }

    public String getProjectId()
    {
        return _projectId;
    }

    public void setProjectId(String projectId)
    {
        _projectId = projectId;
    }

    public String getEventType()
    {
        return _eventType;
    }

    public void setEventType(String eventType)
    {
        _eventType = eventType;
    }

    public String getKey1(){return _key1;}
    public void setKey1(String key){_key1 = key;}
    public String getKey2(){return _key2;}
    public void setKey2(String key){_key2 = key;}
    public Integer getIntKey1(){return _intKey1;}
    public void setIntKey1(Integer key){_intKey1 = key;}
    public Integer getIntKey2(){return _intKey2;}
    public void setIntKey2(Integer key){_intKey2 = key;}
    public String getLsid(){return _lsid;}
    public void setLsid(String lsid){_lsid = lsid;}

    public String getKey3()
    {
        return _key3;
    }

    public void setKey3(String key3)
    {
        _key3 = key3;
    }

    public Integer getIntKey3()
    {
        return _intKey3;
    }

    public void setIntKey3(Integer intKey3)
    {
        _intKey3 = intKey3;
    }
}
