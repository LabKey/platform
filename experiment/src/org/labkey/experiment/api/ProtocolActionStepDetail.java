/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

/**
 * User: jeckels
 * Date: Sep 28, 2005
 */
public class ProtocolActionStepDetail extends IdentifiableEntity
{
    private String _parentProtocolLSID;
    private String _childProtocolLSID;
    private int _actionSequence;
    private int _actionId;
    private String _protocolDescription;
    private String _applicationType;
    private Integer _maxInputMaterialPerInstance;
    private Integer _maxInputDataPerInstance;
    private Integer _outputMaterialPerInstance;
    private Integer _outputDataPerInstance;
    private String _outputMaterialType;
    private String _outputDataType;
    private String _instrument;
    private String _software;
    private String _contactId;

    public String getParentProtocolLSID()
    {
        return _parentProtocolLSID;
    }

    public void setParentProtocolLSID(String parentProtocolLSID)
    {
        _parentProtocolLSID = parentProtocolLSID;
    }

    public String getChildProtocolLSID()
    {
        return _childProtocolLSID;
    }

    public void setChildProtocolLSID(String childProtocolLSID)
    {
        _childProtocolLSID = childProtocolLSID;
    }

    public int getActionSequence()
    {
        return _actionSequence;
    }

    public void setActionSequence(int actionSequence)
    {
        _actionSequence = actionSequence;
    }

    public int getActionId()
    {
        return _actionId;
    }

    public void setActionId(int actionId)
    {
        _actionId = actionId;
    }

    public String getProtocolDescription()
    {
        return _protocolDescription;
    }

    public void setProtocolDescription(String protocolDescription)
    {
        _protocolDescription = protocolDescription;
    }

    public String getApplicationType()
    {
        return _applicationType;
    }

    public void setApplicationType(String applicationType)
    {
        _applicationType = applicationType;
    }

    public Integer getMaxInputMaterialPerInstance()
    {
        return _maxInputMaterialPerInstance;
    }

    public void setMaxInputMaterialPerInstance(Integer maxInputMaterialPerInstance)
    {
        _maxInputMaterialPerInstance = maxInputMaterialPerInstance;
    }

    public Integer getMaxInputDataPerInstance()
    {
        return _maxInputDataPerInstance;
    }

    public void setMaxInputDataPerInstance(Integer maxInputDataPerInstance)
    {
        _maxInputDataPerInstance = maxInputDataPerInstance;
    }

    public Integer getOutputMaterialPerInstance()
    {
        return _outputMaterialPerInstance;
    }

    public void setOutputMaterialPerInstance(Integer outputMaterialPerInstance)
    {
        _outputMaterialPerInstance = outputMaterialPerInstance;
    }

    public Integer getOutputDataPerInstance()
    {
        return _outputDataPerInstance;
    }

    public void setOutputDataPerInstance(Integer outputDataPerInstance)
    {
        _outputDataPerInstance = outputDataPerInstance;
    }

    public String getOutputMaterialType()
    {
        return _outputMaterialType;
    }

    public void setOutputMaterialType(String outputMaterialType)
    {
        _outputMaterialType = outputMaterialType;
    }

    public String getOutputDataType()
    {
        return _outputDataType;
    }

    public void setOutputDataType(String outputDataType)
    {
        _outputDataType = outputDataType;
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public void setInstrument(String instrument)
    {
        _instrument = instrument;
    }

    public String getSoftware()
    {
        return _software;
    }

    public void setSoftware(String software)
    {
        _software = software;
    }

    public String getContactId()
    {
        return _contactId;
    }

    public void setContactId(String contactId)
    {
        _contactId = contactId;
    }
}
