/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateType;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateTypeBean implements PlateType
{
    private Long _rowId;
    private Integer _rows;
    private Integer _cols;
    private String _description;
    private boolean _archived;

    public PlateTypeBean()
    {
    }

    @Override
    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public Integer getCols()
    {
        return _cols;
    }

    @Override
    public Integer getColumns()
    {
        return _cols;
    }

    public void setColumns(Integer cols)
    {
        _cols = cols;
    }

    public void setCols(Integer cols)
    {
        _cols = cols;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    public Integer getRows()
    {
        return _rows;
    }

    public void setRows(Integer rows)
    {
        _rows = rows;
    }

    @Override
    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

    @JsonIgnore
    @Override
    public Integer getWellCount()
    {
        return _rows * _cols;
    }

    @Override
    public int hashCode()
    {
        return (31 * _rows) + _cols;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        if (!Objects.equals(_rows, ((PlateTypeBean) obj)._rows)) return false;
        return Objects.equals(_cols, ((PlateTypeBean) obj)._cols);
    }
}
