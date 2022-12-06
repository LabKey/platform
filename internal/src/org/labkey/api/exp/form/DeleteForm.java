/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
package org.labkey.api.exp.form;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.data.DataRegionSelection;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

/**
 * Created by aaronr on 1/26/15.
 */
public class DeleteForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
{
    private boolean _forceDelete;
    private String _dataRegionSelectionKey;
    private Integer _singleObjectRowId;
    private List<Integer> _rowIds;

    @NotNull
    public Set<Integer> getIds(boolean clear)
    {
        if (getSingleObjectRowId() != null)
            return singleton(getSingleObjectRowId());
        else if (getDataRegionSelectionKey() != null)
            return DataRegionSelection.getSelectedIntegers(getViewContext(), getDataRegionSelectionKey(), clear);
        else if (getRowIds() != null)
            return new HashSet<>(getRowIds());
        else
            return Collections.emptySet();
    }

    public Integer getSingleObjectRowId()
    {
        return _singleObjectRowId;
    }

    public void setSingleObjectRowId(Integer singleObjectRowId)
    {
        _singleObjectRowId = singleObjectRowId;
    }

    public boolean isForceDelete()
    {
        return _forceDelete;
    }

    public void setForceDelete(boolean forceDelete)
    {
        _forceDelete = forceDelete;
    }

    @Override
    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    @Override
    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public void clearSelected()
    {
        if (_singleObjectRowId == null)
            DataRegionSelection.clearAll(getViewContext(), getDataRegionSelectionKey());
    }

    public List<Integer> getRowIds()
    {
        return _rowIds;
    }

    public void setRowIds(List<Integer> rowIds)
    {
        _rowIds = rowIds;
    }
}
