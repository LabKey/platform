/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.BeanUtils;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ProtocolParameter;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean class for the exp.protocol table.
 * User: migra
 * Date: Jun 14, 2005
 */
public class Protocol extends IdentifiableEntity
{
    private String _applicationType;
    private String _contact;
    private String _instrument;
    private Integer _maxInputDataPerInstance;
    private Integer _maxInputMaterialPerInstance;
    private String _outputDataDirTemplate;
    private String _outputDataFileTemplate;
    private String _outputDataLSIDTemplate;
    private String _outputDataNameTemplate;
    private Integer _outputDataPerInstance;
    private String _outputDataType;
    private String _outputMaterialLSIDTemplate;
    private String _outputMaterialNameTemplate;
    private Integer _outputMaterialPerInstance;
    private String _outputMaterialType;
    private String _protocolDescription;
    private String _software;
    private String _contactId;
    private Map<String, ProtocolParameter> _protocolParameters;
    private Map<String, ObjectProperty> _objectProperties;

    public Protocol()
    {
        
    }

    public Protocol(Protocol copyFrom)
    {
        try
        {
            BeanUtils.copyProperties(this, copyFrom);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

    }

    public String getApplicationType()
    {
        return _applicationType;
    }

    public void setApplicationType(String applicationType)
    {
        _applicationType = applicationType;
    }

    public String getContact()
    {
        return _contact;
    }

    public void setContact(String contact)
    {
        _contact = contact;
    }

    public String getInstrument()
    {
        return _instrument;
    }

    public void setInstrument(String instrument)
    {
        _instrument = instrument;
    }

    public Integer getMaxInputDataPerInstance()
    {
        return _maxInputDataPerInstance;
    }

    public void setMaxInputDataPerInstance(Integer maxInputDataPerInstance)
    {
        _maxInputDataPerInstance = maxInputDataPerInstance;
    }

    public Integer getMaxInputMaterialPerInstance()
    {
        return _maxInputMaterialPerInstance;
    }

    public void setMaxInputMaterialPerInstance(Integer maxInputMaterialPerInstance)
    {
        _maxInputMaterialPerInstance = maxInputMaterialPerInstance;
    }

    public String getOutputDataDirTemplate()
    {
        return _outputDataDirTemplate;
    }

    public void setOutputDataDirTemplate(String _outputDataDirTemplate)
    {
        this._outputDataDirTemplate = _outputDataDirTemplate;
    }

    public String getOutputDataFileTemplate()
    {
        return _outputDataFileTemplate;
    }

    public void setOutputDataFileTemplate(String outputDataFileTemplate)
    {
        _outputDataFileTemplate = outputDataFileTemplate;
    }

    public String getOutputDataLSIDTemplate()
    {
        return _outputDataLSIDTemplate;
    }

    public void setOutputDataLSIDTemplate(String outputDataLSIDTemplate)
    {
        _outputDataLSIDTemplate = outputDataLSIDTemplate;
    }

    public String getOutputDataNameTemplate()
    {
        return _outputDataNameTemplate;
    }

    public void setOutputDataNameTemplate(String outputDataNameTemplate)
    {
        _outputDataNameTemplate = outputDataNameTemplate;
    }

    public Integer getOutputDataPerInstance()
    {
        return _outputDataPerInstance;
    }

    public void setOutputDataPerInstance(Integer outputDataPerInstance)
    {
        _outputDataPerInstance = outputDataPerInstance;
    }

    public String getOutputDataType()
    {
        return _outputDataType;
    }

    public void setOutputDataType(String outputDataType)
    {
        _outputDataType = outputDataType;
    }

    public String getOutputMaterialLSIDTemplate()
    {
        return _outputMaterialLSIDTemplate;
    }

    public void setOutputMaterialLSIDTemplate(String outputMaterialLSIDTemplate)
    {
        _outputMaterialLSIDTemplate = outputMaterialLSIDTemplate;
    }

    public String getOutputMaterialNameTemplate()
    {
        return _outputMaterialNameTemplate;
    }

    public void setOutputMaterialNameTemplate(String outputMaterialNameTemplate)
    {
        _outputMaterialNameTemplate = outputMaterialNameTemplate;
    }

    public Integer getOutputMaterialPerInstance()
    {
        return _outputMaterialPerInstance;
    }

    public void setOutputMaterialPerInstance(Integer outputMaterialPerInstance)
    {
        _outputMaterialPerInstance = outputMaterialPerInstance;
    }

    public String getOutputMaterialType()
    {
        return _outputMaterialType;
    }

    public void setOutputMaterialType(String outputMaterialType)
    {
        _outputMaterialType = outputMaterialType;
    }

    public String getProtocolDescription()
    {
        return _protocolDescription;
    }

    public void setProtocolDescription(String protocolDescription)
    {
        _protocolDescription = protocolDescription;
    }

    public String getSoftware()
    {
        return _software;
    }

    public void setSoftware(String software)
    {
        _software = software;
    }

    public String toString()
    {
        return getName();
    }

    public String getContactId()
    {
        return _contactId;
    }

    public void setContactId(String contactId)
    {
        _contactId = contactId;
    }

    public List<Difference> diff(Protocol other) throws SQLException
    {
        List<Difference> result = new ArrayList<>();
        diff(_applicationType, other._applicationType, "Application Type", result);
        diff(_contact, other._contact, "Contact", result);
        diff(_contactId, other._contactId, "Contact Id", result);
        diff(_instrument, other._instrument, "Instrument", result);
        diff(_maxInputDataPerInstance, other._maxInputDataPerInstance, "Max Input Data Per Instance", result);
        diff(_maxInputMaterialPerInstance, other._maxInputMaterialPerInstance, "Max Input Material Per Instance", result);
        diff(_outputDataDirTemplate, other._outputDataDirTemplate, "Output Data Dir Template", result);
        diff(_outputDataFileTemplate, other._outputDataFileTemplate, "Output Data File Template", result);
        diff(_outputDataLSIDTemplate, other._outputDataLSIDTemplate, "Output Data LSID Template", result);
        diff(_outputDataNameTemplate, other._outputDataNameTemplate, "Output Data Name Template", result);
        diff(_outputDataPerInstance, other._outputDataPerInstance, "Output Data Per Instance", result);
        diff(_outputDataType, other._outputDataType, "Output Data Type", result);
        diff(_outputMaterialLSIDTemplate, other._outputMaterialLSIDTemplate, "Output Material LSID Template", result);
        diff(_outputMaterialNameTemplate, other._outputMaterialNameTemplate, "Output Material Name Template", result);
        diff(_outputMaterialPerInstance, other._outputMaterialPerInstance, "Output Material Per Instance", result);
        diff(_outputMaterialType, other._outputMaterialType, "Output Material Type", result);
        diff(_protocolDescription, other._protocolDescription, "Protocol Description", result);
        diff(_software, other._software, "Software", result);
        diff(getName(), other.getName(), "Name", result);
        diff(getContainer(), other.getContainer(), "Container", result);

        // Diff the protocol parameters
        Map<String, ProtocolParameter> thisParams = retrieveProtocolParameters();
        Map<String, ProtocolParameter> otherParams = retrieveProtocolParameters();
        if (!diff(thisParams.entrySet(), otherParams.entrySet(), "Set of ProtocolParameters", result))
        {
            for(ProtocolParameter thisParam : thisParams.values())
            {
                ProtocolParameter otherParam = otherParams.get(thisParam.getOntologyEntryURI());
                if (otherParam == null)
                {
                    result.add(new Difference("ProtocolParameter " + thisParam.getOntologyEntryURI(), thisParam.getValue(), null));
                }
                else
                {
                    diff(thisParam.getName(), otherParam.getName(), "ProtocolParameter " + thisParam.getOntologyEntryURI() + " name", result);
                    diff(thisParam.getValueType(), otherParam.getValueType(), "ProtocolParameter " + thisParam.getOntologyEntryURI() + " type", result);
                    diff(thisParam.getValue(), otherParam.getValue(), "ProtocolParameter " + thisParam.getOntologyEntryURI() + " value", result);
                }
            }
        }

        Map<String, ObjectProperty> thisProps = retrieveObjectProperties();
        Map<String, ObjectProperty> otherProps = other.retrieveObjectProperties();
        diffProperties(thisProps, otherProps, getLSID(), result);

        return result;
    }

    private void diffProperties(Map<String, ObjectProperty> thisProps, Map<String, ObjectProperty> otherProps, String uri, List<Difference> result)
    {
        if (!diff(thisProps.keySet(), otherProps.keySet(), "Properties for " + uri, result))
        {
            for (String propertyURI : thisProps.keySet())
            {
                ObjectProperty thisProp = thisProps.get(propertyURI);
                ObjectProperty otherProp = otherProps.get(propertyURI);
                diff(thisProp.getPropertyType(), otherProp.getPropertyType(), "Properties type for " + thisProp.getPropertyURI() + " of " + uri, result);

                if (thisProp.getPropertyType() == PropertyType.RESOURCE)
                {
                    Map<String, ObjectProperty> thisChildren = thisProp.retrieveChildProperties();
                    Map<String, ObjectProperty> otherChildren = otherProp.retrieveChildProperties();
                    diffProperties(thisChildren, otherChildren, thisProp.getStringValue(), result);
                }
                else
                {
                    diff(thisProp.getDateTimeValue(), otherProp.getDateTimeValue(), "DateTime value for '" + thisProp.getPropertyURI() + "' of '" + uri + "'", result);
                    diff(thisProp.getFloatValue(), otherProp.getFloatValue(), "Float value for property ;" + thisProp.getPropertyURI() + "' of '" + uri + "'", result);
                    diff(thisProp.getStringValue(), otherProp.getStringValue(), "String value for property '" + thisProp.getPropertyURI() + "' of '" + uri + "'", result);
//                    diff(thisProp.getTextValue(), otherProp.getTextValue(), "Text value for " + thisProp.getPropertyURI() + " of " + uri, result);
                }
            }
        }

    }

    public Map<String, ObjectProperty> retrieveObjectProperties()
    {
        if (_objectProperties == null)
        {
            _objectProperties = Collections.unmodifiableMap(OntologyManager.getPropertyObjects(getContainer(), getLSID()));
        }
        return _objectProperties;
    }

    public void storeObjectProperties(Map<String, ObjectProperty> props)
    {
        if (props == null)
        {
            _objectProperties = null;
        }
        else
        {
            Map<String, ObjectProperty> newParams = new HashMap<>();
            newParams.putAll(props);
            _objectProperties = Collections.unmodifiableMap(newParams);
        }
    }

    public void storeProtocolParameters(Collection<ProtocolParameter> protocolParameters)
    {
        Map<String, ProtocolParameter> newParams = new HashMap<>();
        for (ProtocolParameter param : protocolParameters)
        {
            newParams.put(param.getOntologyEntryURI(), param);
        }
        _protocolParameters = Collections.unmodifiableMap(newParams);
    }

    public Map<String, ProtocolParameter> retrieveProtocolParameters()
    {
        if (_protocolParameters == null)
        {
            _protocolParameters = Collections.unmodifiableMap(ExperimentServiceImpl.get().getProtocolParameters(getRowId()));
        }
        return _protocolParameters;
    }
}
