/*
 * Copyright (c) 2018 LabKey Corporation
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
import org.labkey.api.exp.LsidType;

public abstract class AbstractProtocolInput extends IdentifiableBase
{
    /*package*/ static final String NAMESPACE = LsidType.ProtocolInput.name();

    protected int _rowId;
    protected int _protocolId;
    protected boolean _input;
    protected String _criteriaName;
    protected String _criteriaConfig;
    protected int _minOccurs;
    protected Integer _maxOccurs;

    protected AbstractProtocolInput()
    {
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(int protocolId)
    {
        _protocolId = protocolId;
    }

    public boolean isInput()
    {
        return _input;
    }

    public void setInput(boolean input)
    {
        _input = input;
    }

    public abstract String getObjectType();

    public final void setObjectType(String objectType)
    {
        // ignore - getter is a constant in derived classes
    }

    public String getCriteriaName()
    {
        return _criteriaName;
    }

    public void setCriteriaName(String criteriaName)
    {
        _criteriaName = criteriaName;
    }

    public String getCriteriaConfig()
    {
        return _criteriaConfig;
    }

    public void setCriteriaConfig(String criteriaConfig)
    {
        _criteriaConfig = criteriaConfig;
    }

    public int getMinOccurs()
    {
        return _minOccurs;
    }

    public void setMinOccurs(int minOccurs)
    {
        _minOccurs = minOccurs;
    }

    public Integer getMaxOccurs()
    {
        return _maxOccurs;
    }

    public void setMaxOccurs(Integer maxOccurs)
    {
        _maxOccurs = maxOccurs;
    }
}
