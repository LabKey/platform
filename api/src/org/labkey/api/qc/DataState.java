/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.api.qc;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.GUID;

import java.util.Objects;

public class DataState
{
    private long _rowId;
    private String _label;
    private GUID _containerId;
    private String _description;
    private boolean _publicData;
    private String _stateType;
    private String _color;

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_containerId);
    }

    public void setContainer(@Nullable Container container)
    {
        _containerId = container != null ? container.getEntityId() : null;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isPublicData()
    {
        return _publicData;
    }

    public void setPublicData(boolean publicData)
    {
        _publicData = publicData;
    }

    public String getStateType()
    {
        return _stateType;
    }

    public void setStateType(String stateType)
    {
        _stateType = stateType;
    }

    public boolean isQCState()
    {
        return _stateType == null;
    }

    public String getColor()
    {
        return _color;
    }

    public void setColor(String color)
    {
        _color = color;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataState qcState = (DataState) o;

        if (_publicData != qcState._publicData) return false;
        if (_rowId != qcState._rowId) return false;
        if (!Objects.equals(_containerId, qcState._containerId)) return false;
        if (!Objects.equals(_description, qcState._description))
            return false;
        if (!Objects.equals(_label, qcState._label)) return false;

        return Objects.equals(_stateType, qcState._stateType);
    }

    public int hashCode()
    {
        int result;
        result = (int)_rowId;
        result = 31 * result + (_label != null ? _label.hashCode() : 0);
        result = 31 * result + (_containerId != null ? _containerId.hashCode() : 0);
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        result = 31 * result + (_publicData ? 1 : 0);
        result = 31 * result + (_stateType != null ? _stateType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return getLabel() + (getStateType() == null ? "" : " (type: " + getStateType() + ")") +  (getDescription() == null ? "" : ": " + getDescription());
    }
}
