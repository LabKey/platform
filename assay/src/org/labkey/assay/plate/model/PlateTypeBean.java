package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateType;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateTypeBean implements PlateType
{
    private Integer _rowId;
    private Integer _rows;
    private Integer _cols;
    private String _description;
    private boolean _archived;

    public PlateTypeBean()
    {
    }

    @Override
    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
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

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Integer getRows()
    {
        return _rows;
    }

    public void setRows(Integer rows)
    {
        _rows = rows;
    }

    public boolean isArchived()
    {
        return _archived;
    }

    public void setArchived(boolean archived)
    {
        _archived = archived;
    }

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
