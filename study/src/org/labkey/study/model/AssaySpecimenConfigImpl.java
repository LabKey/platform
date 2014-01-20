/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.study.AssaySpecimenConfig;

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
    private String _lab;
    private String _sampleType;

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

    public String getLab()
    {
        return _lab;
    }

    public void setLab(String lab)
    {
        _lab = lab;
    }

    public String getSampleType()
    {
        return _sampleType;
    }

    public void setSampleType(String sampleType)
    {
        _sampleType = sampleType;
    }
}
