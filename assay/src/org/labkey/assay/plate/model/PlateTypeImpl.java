package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateType;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateTypeImpl implements PlateType
{
    private Integer _rowId;
    private String _assayType;
    private Integer _cols;
    private String _description;
    private Integer _rows;
    private String _type;

    public PlateTypeImpl()
    {
    }

    public PlateTypeImpl(String assayType, String type, String description, Integer rows, Integer cols)
    {
        _assayType = assayType;
        _type = type;
        _description = description;
        _cols = cols;
        _rows = rows;
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

    public String getAssayType()
    {
        return _assayType;
    }

    public void setAssayType(String assayType)
    {
        _assayType = assayType;
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

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
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

        if (!Objects.equals(_rows, ((PlateTypeImpl) obj)._rows)) return false;
        return Objects.equals(_cols, ((PlateTypeImpl) obj)._cols);
    }
}
