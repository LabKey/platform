/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.util.*;
import java.sql.SQLException;

public class ExpProtocolApplicationImpl extends ExpIdentifiableBaseImpl<ProtocolApplication> implements ExpProtocolApplication
{
    private List<ExpMaterial> _inputMaterials;
    private List<ExpData> _inputDatas;
    private List<ExpMaterial> _outputMaterials;
    private List<ExpData> _outputDatas;

    public ExpProtocolApplicationImpl(ProtocolApplication app)
    {
        super(app);
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public User getCreatedBy()
    {
        ExpProtocolImpl protocol = getProtocol();
        return null == protocol ? null : protocol.getCreatedBy();
    }

    public String getContainerId()
    {
        return getRun().getContainer().getId();
    }
    
    public void setContainerId(String containerId)
    {
        throw new UnsupportedOperationException();
    }

    public ExpDataInput[] getDataInputs()
    {
        return ExpDataInputImpl.fromInputs(ExperimentServiceImpl.get().getDataInputsForApplication(getRowId()));
    }

    public List<ExpData> getInputDatas()
    {
        if (_inputDatas == null)
        {
            ExpDataInput[] inputs = getDataInputs();
            _inputDatas= new ArrayList<ExpData>(inputs.length);
            for (ExpDataInput input : inputs)
            {
                _inputDatas.add(input.getData());
            }
            Collections.sort(_inputDatas, ExpObject.NAME_COMPARATOR);
        }
        return _inputDatas;
    }

    public List<ExpMaterial> getInputMaterials()
    {
        if (_inputMaterials == null)
        {
            ExpMaterialInput[] inputs = getMaterialInputs();
            _inputMaterials = new ArrayList<ExpMaterial>(inputs.length);
            for (ExpMaterialInput input : inputs)
            {
                _inputMaterials.add(input.getMaterial());
            }
            Collections.sort(_inputMaterials, ExpObject.NAME_COMPARATOR);
        }
        return _inputMaterials;
    }

    public List<ExpData> getOutputDatas()
    {
        if (_outputDatas == null)
        {
            _outputDatas = new ArrayList<ExpData>(Arrays.asList(ExpDataImpl.fromDatas(ExperimentServiceImpl.get().getOutputDataForApplication(getRowId()))));
            Collections.sort(_outputDatas, ExpObject.NAME_COMPARATOR);
        }
        return _outputDatas;
    }

    public ExpMaterialInput[] getMaterialInputs()
    {
        return ExpMaterialInputImpl.fromInputs(ExperimentServiceImpl.get().getMaterialInputsForApplication(getRowId()));
    }

    public List<ExpMaterial> getOutputMaterials()
    {
        if (_outputMaterials == null)
        {
            _outputMaterials = new ArrayList<ExpMaterial>(Arrays.asList(ExpMaterialImpl.fromMaterials(ExperimentServiceImpl.get().getOutputMaterialForApplication(getRowId()))));
            Collections.sort(_outputMaterials, ExpObject.NAME_COMPARATOR);
        }
        return _outputMaterials;
    }

    public ExpProtocolImpl getProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolLSID());
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public ExpRunImpl getRun()
    {
        Integer runId = _object.getRunId();
        return runId == null ? null : ExperimentServiceImpl.get().getExpRun(runId.intValue());
    }

    public int getActionSequence()
    {
        return _object.getActionSequence();
    }

    public ExpProtocol.ApplicationType getApplicationType()
    {
        return ExpProtocol.ApplicationType.valueOf(_object.getCpasType());
    }

    public Date getActivityDate()
    {
        return _object.getActivityDate();
    }

    public String getComments()
    {
        return _object.getComments();
    }

    public String getCpasType()
    {
        return _object.getCpasType();
    }

    public void save(User user)
    {
        try
        {
            if (getRowId() == 0)
            {
                _object = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), _object);
            }
            else
            {
                _object = Table.update(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), _object, getRowId(), null);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setInputMaterials(List<ExpMaterial> inputMaterialList)
    {
        _inputMaterials = inputMaterialList;
    }

    public void setInputDatas(List<ExpData> inputDataList)
    {
        _inputDatas = inputDataList;
    }

    public void setOutputMaterials(List<ExpMaterial> outputMaterialList)
    {
        _outputMaterials = outputMaterialList;
    }

    public void setOutputDatas(List<ExpData> outputDataList)
    {
        _outputDatas = outputDataList;
    }
    
    public PropertyDescriptor addDataInput(User user, ExpData data, String roleName, PropertyDescriptor pd)
    {
        try
        {
            if (pd == null)
            {
                pd = ExperimentService.get().ensureDataInputRole(user, getContainer(), roleName, data);
            }
            DataInput obj = new DataInput();
            obj.setDataId(data.getRowId());
            obj.setTargetApplicationId(getRowId());
            if (pd != null)
            {
                obj.setPropertyId(pd.getPropertyId());
            }

            Table.insert(user, ExperimentServiceImpl.get().getTinfoDataInput(), obj);
            return pd;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public PropertyDescriptor addMaterialInput(User user, ExpMaterial material, String roleName, PropertyDescriptor pd)
    {
        try
        {
            if (pd == null)
            {
                pd = ExperimentService.get().ensureMaterialInputRole(getContainer(), roleName, material);
            }
            MaterialInput obj = new MaterialInput();
            obj.setMaterialId(material.getRowId());
            obj.setTargetApplicationId(getRowId());
            if (pd != null)
            {
                obj.setPropertyId(pd.getPropertyId());
            }
            Table.insert(user, ExperimentServiceImpl.get().getTinfoMaterialInput(), obj);
            return pd;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void removeDataInput(User user, ExpData data) throws Exception
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("TargetApplicationId", getRowId());
        filter.addCondition("DataId", data.getRowId());
        Table.delete(ExperimentServiceImpl.get().getTinfoDataInput(), filter);
    }

    public void removeMaterialInput(User user, ExpMaterial material) throws Exception
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("TargetApplicationId", getRowId());
        filter.addCondition("MaterialId", material.getRowId());
        Table.delete(ExperimentServiceImpl.get().getTinfoMaterialInput(), filter);
    }

    public static ExpProtocolApplicationImpl[] fromProtocolApplications(ProtocolApplication[] apps)
    {
        ExpProtocolApplicationImpl[] result = new ExpProtocolApplicationImpl[apps.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = new ExpProtocolApplicationImpl(apps[i]);
        }
        return result;
    }

    public String toString()
    {
        return "ProtocolApplication, LSID: " + getLSID() + (getRun() == null ? null : ( ", run: " + getRun().getLSID()));  
    }
}
