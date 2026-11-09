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
package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a row in the plate.well table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WellBean
{
    private Integer _rowId;
    private String _lsid;
    private Integer _plateId;
    private Integer _row;
    private Integer _col;
    private String _container;
    private Long _sampleId;

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
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

    public Integer getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Integer plateId)
    {
        _plateId = plateId;
    }

    public Integer getRow()
    {
        return _row;
    }

    public void setRow(Integer row)
    {
        _row = row;
    }

    public Integer getCol()
    {
        return _col;
    }

    public void setCol(Integer col)
    {
        _col = col;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Long getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(Long sampleId)
    {
        _sampleId = sampleId;
    }
}
