/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.exp.IdentifiableBase;

import java.util.Date;

/**
 * Input or output of a run, like a data file or a material.
 * User: jeckels
 * Date: Oct 17, 2005
 */
public abstract class RunItem extends IdentifiableBase
{
    private Integer _runId;
    private int _rowId;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;
    private String _cpasType;
    private Integer _sourceApplicationId;
    private String _description;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getCpasType()
    {
        return _cpasType;
    }

    public void setCpasType(String cpasType)
    {
        _cpasType = cpasType;
    }

    public Integer getSourceApplicationId()
    {
        return _sourceApplicationId;
    }

    public void setSourceApplicationId(Integer sourceApplicationId)
    {
        _sourceApplicationId = sourceApplicationId;
    }

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public int hashCode()
    {
        return _rowId + getClass().hashCode();
    }
}
