/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.jetbrains.annotations.NotNull;

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

    public Date getCreated()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getCreated();
    }

    public User getCreatedBy()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getCreatedBy();
    }

    public User getModifiedBy()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getModifiedBy();
    }

    public Date getModified()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getModified();
    }

    public Container getContainer()
    {
        return getRun().getContainer();
    }
    
    public void setContainer(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public ExpDataRunInput[] getDataInputs()
    {
        return ExpDataRunInputImpl.fromInputs(ExperimentServiceImpl.get().getDataInputsForApplication(getRowId()));
    }

    @NotNull
    public List<ExpData> getInputDatas()
    {
        if (_inputDatas == null)
        {
            ExpDataRunInput[] inputs = getDataInputs();
            _inputDatas= new ArrayList<ExpData>(inputs.length);
            for (ExpDataRunInput input : inputs)
            {
                _inputDatas.add(input.getData());
            }
            Collections.sort(_inputDatas);
        }
        return _inputDatas;
    }

    @NotNull
    public List<ExpMaterial> getInputMaterials()
    {
        if (_inputMaterials == null)
        {
            ExpMaterialRunInput[] inputs = getMaterialInputs();
            _inputMaterials = new ArrayList<ExpMaterial>(inputs.length);
            for (ExpMaterialRunInput input : inputs)
            {
                _inputMaterials.add(input.getMaterial());
            }
            Collections.sort(_inputMaterials);
        }
        return _inputMaterials;
    }

    @NotNull
    public List<ExpData> getOutputDatas()
    {
        if (_outputDatas == null)
        {
            _outputDatas = new ArrayList<ExpData>(Arrays.asList(ExpDataImpl.fromDatas(ExperimentServiceImpl.get().getOutputDataForApplication(getRowId()))));
            Collections.sort(_outputDatas);
        }
        return _outputDatas;
    }

    @NotNull
    public ExpMaterialRunInput[] getMaterialInputs()
    {
        return ExpMaterialRunInputImpl.fromInputs(ExperimentServiceImpl.get().getMaterialInputsForApplication(getRowId()));
    }

    @NotNull
    public List<ExpMaterial> getOutputMaterials()
    {
        if (_outputMaterials == null)
        {
            _outputMaterials = new ArrayList<ExpMaterial>(Arrays.asList(ExpMaterialImpl.fromMaterials(ExperimentServiceImpl.get().getOutputMaterialForApplication(getRowId()))));
            Collections.sort(_outputMaterials);
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

    public void setRun(ExpRun run)
    {
        ensureUnlocked();
        _object.setRunId(run.getRowId());
    }

    public void setActionSequence(int actionSequence)
    {
        ensureUnlocked();
        _object.setActionSequence(actionSequence);
    }

    public void setProtocol(ExpProtocol protocol)
    {
        ensureUnlocked();
        _object.setProtocolLSID(protocol.getLSID());
        _object.setCpasType(protocol.getApplicationType().toString());
    }

    public void setActivityDate(Date date)
    {
        ensureUnlocked();
        _object.setActivityDate(date);
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

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoProtocolApplication());
    }

    public void delete(User user)
    {
        if (getRowId() != 0)
        {
            try
            {
                Table.delete(ExperimentServiceImpl.get().getTinfoDataInput(), new SimpleFilter("TargetApplicationId", getRowId()));
                Table.delete(ExperimentServiceImpl.get().getTinfoMaterialInput(), new SimpleFilter("TargetApplicationId", getRowId()));
                Table.delete(ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), new SimpleFilter("ProtocolApplicationId", getRowId()));

                SQLFragment commonSQL = new SQLFragment(" SET SourceApplicationId = NULL, RunId = NULL WHERE SourceApplicationId = ?", getRowId());

                SQLFragment materialSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoMaterial());
                materialSQL.append(commonSQL);
                Table.execute(ExperimentServiceImpl.get().getSchema(), materialSQL);

                SQLFragment dataSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoData());
                dataSQL.append(commonSQL);
                Table.execute(ExperimentServiceImpl.get().getSchema(), dataSQL);

                Table.delete(ExperimentServiceImpl.get().getTinfoProtocolApplication(), getRowId());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }

    public void setInputMaterials(List<ExpMaterial> inputMaterialList)
    {
        ensureUnlocked();
        _inputMaterials = inputMaterialList;
    }

    public void setInputDatas(List<ExpData> inputDataList)
    {
        ensureUnlocked();
        _inputDatas = inputDataList;
    }

    public void setOutputMaterials(List<ExpMaterial> outputMaterialList)
    {
        ensureUnlocked();
        _outputMaterials = outputMaterialList;
    }

    public void setOutputDatas(List<ExpData> outputDataList)
    {
        ensureUnlocked();
        _outputDatas = outputDataList;
    }
    
    public void addDataInput(User user, ExpData data, String roleName)
    {
        try
        {
            DataInput obj = new DataInput();
            obj.setDataId(data.getRowId());
            obj.setTargetApplicationId(getRowId());
            obj.setRole(roleName);

            Table.insert(user, ExperimentServiceImpl.get().getTinfoDataInput(), obj);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void addMaterialInput(User user, ExpMaterial material, @Nullable String roleName)
    {
        try
        {
            MaterialInput obj = new MaterialInput();
            obj.setMaterialId(material.getRowId());
            obj.setTargetApplicationId(getRowId());
            obj.setRole(roleName);
            Table.insert(user, ExperimentServiceImpl.get().getTinfoMaterialInput(), obj);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void removeDataInput(User user, ExpData data)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("TargetApplicationId", getRowId());
            filter.addCondition("DataId", data.getRowId());
            Table.delete(ExperimentServiceImpl.get().getTinfoDataInput(), filter);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void removeMaterialInput(User user, ExpMaterial material)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("TargetApplicationId", getRowId());
            filter.addCondition("MaterialId", material.getRowId());
            Table.delete(ExperimentServiceImpl.get().getTinfoMaterialInput(), filter);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
