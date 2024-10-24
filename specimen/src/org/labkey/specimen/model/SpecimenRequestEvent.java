/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

package org.labkey.specimen.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.study.AbstractStudyCachable;

import java.util.Date;
import java.util.Objects;

public class SpecimenRequestEvent extends AbstractStudyCachable<Integer, SpecimenRequestEvent> implements AttachmentParent
{
    private int _rowId;
    private String _entityId;
    private int _createdBy;
    private long _created;
    private Container _container;
    private int _requestId;
    private Integer _requirementId;
    private String _comments;
    private String _entryType;

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        verifyMutability();
        _comments = comments;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public String getContainerId()
    {
        return _container.getId();
    }

    @Override
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
        _created = created.getTime();
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        verifyMutability();
        _createdBy = createdBy;
    }

    @Override
    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        verifyMutability();
        _entityId = entityId;
    }

    public String getEntryType()
    {
        return _entryType;
    }

    public void setEntryType(String entryType)
    {
        verifyMutability();
        _entryType = entryType;
    }

    public int getRequestId()
    {
        return _requestId;
    }

    public void setRequestId(int requestId)
    {
        verifyMutability();
        _requestId = requestId;
    }

    @Override
    public Integer getPrimaryKey()
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

    public Integer getRequirementId()
    {
        return _requirementId;
    }

    public void setRequirementId(Integer requirementId)
    {
        verifyMutability();
        _requirementId = requirementId;
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return SpecimenRequestEventType.get();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpecimenRequestEvent that = (SpecimenRequestEvent) o;
        return _rowId == that._rowId && _createdBy == that._createdBy && _created == that._created && _requestId == that._requestId && Objects.equals(_entityId, that._entityId) && Objects.equals(_container, that._container) && Objects.equals(_requirementId, that._requirementId) && Objects.equals(_comments, that._comments) && Objects.equals(_entryType, that._entryType);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId, _entityId, _createdBy, _created, _container, _requestId, _requirementId, _comments, _entryType);
    }
}
