/*
 * Copyright (c) 2015 LabKey Corporation
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

import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.study.actions.ProtocolIdForm;
import org.labkey.api.util.PageFlowUtil;

/**
 * Created by aaronr on 1/26/15.
 */
public class DeleteForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
{
    private boolean _forceDelete;
    private String _dataRegionSelectionKey;
    private Integer _singleObjectRowId;

    public int[] getIds(boolean clear)
    {
        if (_singleObjectRowId != null)
        {
            return new int[] {_singleObjectRowId};
        }
        return PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), clear));
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

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }
}
