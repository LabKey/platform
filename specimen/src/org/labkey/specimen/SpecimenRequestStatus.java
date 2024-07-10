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

package org.labkey.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.study.AbstractStudyCachable;

import java.util.Objects;

public class SpecimenRequestStatus extends AbstractStudyCachable<Integer, SpecimenRequestStatus>
{
    private int _rowId;
    private Container _container;
    private Integer _sortOrder;
    private String _label;
    private boolean _specimensLocked;
    private boolean _finalState;

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
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

    public Integer getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        verifyMutability();
        _sortOrder = sortOrder;
    }

    public boolean isFinalState()
    {
        return _finalState;
    }

    public void setFinalState(boolean finalState)
    {
        verifyMutability();
        _finalState = finalState;
    }

    public boolean isSpecimensLocked()
    {
        return _specimensLocked;
    }

    public void setSpecimensLocked(boolean specimensLocked)
    {
        verifyMutability();
        _specimensLocked = specimensLocked;
    }

    public boolean isSystemStatus()
    {
        return _sortOrder < 0;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpecimenRequestStatus that = (SpecimenRequestStatus) o;
        return _rowId == that._rowId && _specimensLocked == that._specimensLocked && _finalState == that._finalState && Objects.equals(_container, that._container) && Objects.equals(_sortOrder, that._sortOrder) && Objects.equals(_label, that._label);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId, _container, _sortOrder, _label, _specimensLocked, _finalState);
    }
}
