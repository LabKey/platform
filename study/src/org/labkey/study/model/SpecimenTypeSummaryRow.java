/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

/**
 * User: brittp
 * Created: Jan 31, 2008 11:34:47 AM
 */
public class SpecimenTypeSummaryRow
{
    private String _primaryType;
    private Integer _primaryTypeId;
    private String _derivative;
    private Integer _derivativeTypeId;
    private String _additive;
    private Integer _additiveTypeId;
    private Integer _vialCount;

    public String getPrimaryType()
    {
        return _primaryType;
    }

    public void setPrimaryType(String primaryType)
    {
        _primaryType = primaryType;
    }

    public String getDerivative()
    {
        return _derivative;
    }

    public void setDerivative(String derivative)
    {
        _derivative = derivative;
    }

    public String getAdditive()
    {
        return _additive;
    }

    public void setAdditive(String additive)
    {
        _additive = additive;
    }

    public Integer getVialCount()
    {
        return _vialCount != null ? _vialCount : new Integer(0);
    }

    public void setVialCount(Integer vialCount)
    {
        _vialCount = vialCount;
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

    public Integer getAdditiveTypeId()
    {
        return _additiveTypeId;
    }

    public void setAdditiveTypeId(Integer additiveTypeId)
    {
        _additiveTypeId = additiveTypeId;
    }
}
