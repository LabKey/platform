package org.labkey.study.model;

/**
 * Copyright (c) 2007 LabKey Software Foundation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 31, 2008 11:34:47 AM
 */
public class SpecimenTypeSummaryRow
{
    private Integer _primaryTypeId;
    private String _primaryType;
    private Integer _derivativeId;
    private String _derivative;
    private Integer _additiveId;
    private String _additive;
    private int _vialCount;

    public Integer getPrimaryTypeId()
    {
        return _primaryTypeId;
    }

    public void setPrimaryTypeId(Integer primaryTypeId)
    {
        _primaryTypeId = primaryTypeId;
    }

    public String getPrimaryType()
    {
        return _primaryType;
    }

    public void setPrimaryType(String primaryType)
    {
        _primaryType = primaryType;
    }

    public Integer getDerivativeId()
    {
        return _derivativeId;
    }

    public void setDerivativeId(Integer derivativeId)
    {
        _derivativeId = derivativeId;
    }

    public String getDerivative()
    {
        return _derivative;
    }

    public void setDerivative(String derivative)
    {
        _derivative = derivative;
    }

    public Integer getAdditiveId()
    {
        return _additiveId;
    }

    public void setAdditiveId(Integer additiveId)
    {
        _additiveId = additiveId;
    }

    public String getAdditive()
    {
        return _additive;
    }

    public void setAdditive(String additive)
    {
        _additive = additive;
    }

    public int getVialCount()
    {
        return _vialCount;
    }

    public void setVialCount(int vialCount)
    {
        _vialCount = vialCount;
    }
}
