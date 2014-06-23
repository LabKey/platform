/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.study.StudyCachable;
import org.labkey.api.util.MemTracker;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudySchema;
import org.labkey.study.requirements.DefaultRequirement;

/**
 * User: brittp
 * Date: Feb 8, 2006
 * Time: 4:18:29 PM
 */
public class SpecimenRequestRequirement extends DefaultRequirement<SpecimenRequestRequirement>
        implements StudyCachable<SpecimenRequestRequirement>, Cloneable
{
    private int _rowId;
    private Container _container;
    private Integer _requestId;
    private String _ownerEntityId;
    private Integer _actorId;
    private Integer _siteId;        // This is a locationId, but must match column name in table
    private boolean _complete;
    private String _description;
    private boolean _mutable = true;

    public SpecimenRequestRequirement()
    {
        MemTracker.getInstance().put(this);
    }

    protected void verifyMutability()
    {
        if (!_mutable)
            throw new IllegalStateException("Cached objects are immutable; createMutable must be called first.");
    }

    public boolean isMutable()
    {
        return _mutable;
    }

    protected void unlock()
    {
        _mutable = true;
    }

    public void lock()
    {
        _mutable = false;
    }


    public SpecimenRequestRequirement createMutable()
    {
        try
        {
            SpecimenRequestRequirement obj = (SpecimenRequestRequirement) clone();
            obj.unlock();
            return obj;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public SpecimenRequestActor getActor()
    {
        if (_actorId == null)
            return null;
        return SpecimenManager.getInstance().getRequirementsProvider().getActor(getContainer(), _actorId);
    }

    public LocationImpl getLocation()
    {
        if (_siteId == null)
            return null;
        return StudyManager.getInstance().getLocation(_container, _siteId);
    }

    public Integer getActorId()
    {
        return _actorId;
    }

    public void setActorId(Integer actorId)
    {
        verifyMutability();
        _actorId = actorId;
    }

    public boolean isComplete()
    {
        return _complete;
    }

    public void setComplete(boolean complete)
    {
        verifyMutability();
        _complete = complete;
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

    public Integer getRequestId()
    {
        return _requestId;
    }

    public void setRequestId(Integer requestId)
    {
        verifyMutability();
        _requestId = requestId;
    }

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

    public Integer getSiteId()
    {
        return _siteId;
    }

    public void setSiteId(Integer siteId)
    {
        verifyMutability();
        _siteId = siteId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getRequirementSummary()
    {
        StringBuilder builder = new StringBuilder();
        if (_actorId != null)
            builder.append(getActor().getLabel());
        if (_siteId != null)
            builder.append(" (").append(getLocation().getLabel()).append(")");
        if (_description != null)
        {
            if (builder.length() > 0 && _description.length() > 0)
                builder.append(", ");
            builder.append(_description);
        }
        return builder.toString();
    }

    public String getOwnerEntityId()
    {
        return _ownerEntityId;
    }

    public void setOwnerEntityId(String requestEntityId)
    {
        _ownerEntityId = requestEntityId;
    }


    public Object getActorPrimaryKey()
    {
        return _actorId;
    }
    
    private int safeIntegerCompare(Integer first, Integer second)
    {
        if (first == null && second == null)
            return 0;
        if (first == null)
            return -1;
        if (second == null)
            return 1;
        return first.compareTo(second);
    }

    public boolean isEqual(SpecimenRequestRequirement other)
    {
        if (this == other)
            return true;

        int comp = safeIntegerCompare(getSiteId(), other.getSiteId());
        if (comp == 0)
            comp = getDescription().compareTo(other.getDescription());
        if (comp == 0)
            comp = safeIntegerCompare(getActorId(), other.getActorId());
        if (comp == 0)
            comp = (isComplete() ? 1 : 0) - (other.isComplete() ? 1 : 0);
        return comp == 0;
    }


    protected Object getPrimaryKeyValue()
    {
        return getRowId();
    }

    protected TableInfo getTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestRequirement();
    }
}
