/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.data.DataRegionSelection;

import java.util.Set;

public class DataViewSelectionForm extends ViewForm
{
    protected String _dataRegionSelectionKey;
    protected Set<Long> _rowIds;

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public Set<Long> getRowIds()
    {
        return _rowIds;
    }

    public void setRowIds(Set<Long> rowIds)
    {
        _rowIds = rowIds;
    }

    public Set<Long> getIds(boolean clear)
    {
        return (_rowIds != null) ? _rowIds : DataRegionSelection.getSelectedIntegers(getViewContext(), getDataRegionSelectionKey(), clear);
    }
}
