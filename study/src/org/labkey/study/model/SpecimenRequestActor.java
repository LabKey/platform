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
import org.labkey.study.requirements.DefaultActor;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Feb 8, 2006
 * Time: 4:18:20 PM
 */
public class SpecimenRequestActor extends DefaultActor<SpecimenRequestActor>
        implements StudyCachable<SpecimenRequestActor>, Cloneable
{
    private int _rowId;
    private Integer _sortOrder;
    private Container _container;
    private String _label;
    private boolean _perSite;

    private boolean _mutable = true;

    public SpecimenRequestActor()
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


    public SpecimenRequestActor createMutable()
    {
        try
        {
            SpecimenRequestActor obj = (SpecimenRequestActor) clone();
            obj.unlock();
            return obj;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
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

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
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

    public Integer getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        verifyMutability();
        _sortOrder = sortOrder;
    }

    public boolean isPerSite()
    {
        return _perSite;
    }

    public void setPerSite(boolean perSite)
    {
        verifyMutability();
        _perSite = perSite;
    }

    public String getGroupName()
    {
        return getLabel();
    }


    protected TableInfo getTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestActor();
    }
}
