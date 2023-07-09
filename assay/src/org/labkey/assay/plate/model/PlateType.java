package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateType
{
    private String _assayType;
    private Integer _cols;
    private String _description;
    private Integer _rows;
    private String _type;

    public PlateType()
    {
    }

    public PlateType(String assayType, String type, String description, Integer cols, Integer rows)
    {
        _assayType = assayType;
        _type = type;
        _description = description;
        _cols = cols;
        _rows = rows;
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
}
