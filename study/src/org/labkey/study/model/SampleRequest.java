/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.study.Site;
import org.labkey.study.SampleManager;
import org.labkey.study.requirements.RequirementOwner;

import java.util.Date;

/**
 * User: brittp
 * Date: Feb 8, 2006
 * Time: 1:50:53 PM
 */
public class SampleRequest extends AbstractStudyCachable<SampleRequest> implements RequirementOwner
{
    private Container _container;
    private String _entityId;
    private int _rowId;
    private int _statusId;
    private int _createdBy;
    private long _created;
    private int _modifiedBy;
    private long _modified;
    private String _comments;
    private Integer _destinationSiteId;
    private boolean _hidden;

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        verifyMutability();
        _rowId = rowId;
    }

    public int getStatusId()
    {
        return _statusId;
    }

    public void setStatusId(int statusId)
    {
        verifyMutability();
        _statusId = statusId;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        verifyMutability();
        _comments = comments;
    }

    public Integer getDestinationSiteId()
    {
        return _destinationSiteId;
    }

    public void setDestinationSiteId(Integer destinationSiteId)
    {
        verifyMutability();
        _destinationSiteId = destinationSiteId;
    }

    public Specimen[] getSpecimens()
    {
        return SampleManager.getInstance().getRequestSpecimens(this);
    }

    public SampleRequestRequirement[] getRequirements()
    {
        return SampleManager.getInstance().getRequirementsProvider().getRequirements(this);
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        verifyMutability();
        _hidden = hidden;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        verifyMutability();
        _container = container;
    }

    public Date getCreated()
    {
        return new Date(_created);
    }

    public void setCreated(Date created)
    {
        verifyMutability();
        this._created = created.getTime();
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        verifyMutability();
        this._createdBy = createdBy;
    }

    public Date getModified()
    {
        return new Date(_modified);
    }

    public void setModified(Date modified)
    {
        verifyMutability();
        this._modified = modified.getTime();
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        verifyMutability();
        this._modifiedBy = modifiedBy;
    }

    public String getRequestDescription()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Request ID ").append(_rowId);
        if (_destinationSiteId != null)
        {
            Site destination = StudyManager.getInstance().getSite(_container, _destinationSiteId);
            builder.append(", destination ").append(destination.getDisplayName());
        }
        return builder.toString();
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }
}
