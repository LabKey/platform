package org.labkey.study.model;

import org.labkey.api.data.Entity;
import org.labkey.api.study.AssaySpecimenConfig;

import java.util.List;

/**
 * User: cnathe
 * Date: 12/14/13
 */

/**
 * Represents an assay/specimen configuration for a study
 */
public class AssaySpecimenConfigImpl extends AbstractStudyEntity<AssaySpecimenConfigImpl> implements AssaySpecimenConfig
{
    private int _rowId;
    private String _assayName;
    private String _description;
    private String _source;
    private Integer _locationId;
    private Integer _primaryTypeId;
    private Integer _derivativeTypeId;
    private String _tubeType;

    public AssaySpecimenConfigImpl()
    {
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getAssayName()
    {
        return _assayName;
    }

    public void setAssayName(String assayName)
    {
        _assayName = assayName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSource()
    {
        return _source;
    }

    public void setSource(String source)
    {
        _source = source;
    }

    public Integer getLocationId()
    {
        return _locationId;
    }

    public void setLocationId(Integer locationId)
    {
        _locationId = locationId;
    }

    public Integer getPrimaryTypeId()
    {
        return _primaryTypeId;
    }

    public void setPrimaryTypeId(Integer primaryTypeId)
    {
        _primaryTypeId = primaryTypeId;
    }

    public Integer getDerivativeTypeId()
    {
        return _derivativeTypeId;
    }

    public void setDerivativeTypeId(Integer derivativeTypeId)
    {
        _derivativeTypeId = derivativeTypeId;
    }

    public String getTubeType()
    {
        return _tubeType;
    }

    public void setTubeType(String tubeType)
    {
        _tubeType = tubeType;
    }
}
