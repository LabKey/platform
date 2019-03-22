/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.assay.nab;

import org.labkey.api.data.statistics.StatsService;

/**
 * User: klum
 * Date: 5/15/13
 */
public class RenderAssayForm
{
    private boolean _newRun;
    private int _rowId = -1;
    protected StatsService.CurveFitType _fitType;

    public boolean isNewRun()
    {
        return _newRun;
    }

    public void setNewRun(boolean newRun)
    {
        _newRun = newRun;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getFitType()
    {
        return _fitType != null ? _fitType.name() : null;
    }

    public void setFitType(String fitType)
    {
        _fitType = fitType != null ? StatsService.CurveFitType.valueOf(fitType) : null;
    }

    public StatsService.CurveFitType getFitTypeEnum()
    {
        return _fitType;
    }
}
