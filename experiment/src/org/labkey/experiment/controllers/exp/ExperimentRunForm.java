/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.ViewFormData;
import org.labkey.api.view.HttpView;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ExperimentRunForm extends ViewFormData
{
    private int _rowId;
    private String _lsid;
    private boolean _detail;
    private String _focus;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public ExpRunImpl lookupRun()
    {
        ExpRunImpl run = ExperimentServiceImpl.get().getExpRun(getRowId());
        if (run == null && getLsid() != null)
        {
            run = ExperimentServiceImpl.get().getExpRun(getLsid());
        }
        if (run == null)
        {
            HttpView.throwNotFound("Could not find experiment run");
        }
        return run;
    }

    public boolean isDetail()
    {
        return _detail;
    }

    public String getFocus()
    {
        return _focus;
    }

    public void setFocus(String focus)
    {
        _focus = focus;
    }

    public void setDetail(boolean detail)
    {
        _detail = detail;
    }
}
