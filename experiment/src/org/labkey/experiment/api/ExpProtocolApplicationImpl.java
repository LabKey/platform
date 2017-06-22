/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ExpProtocolApplicationImpl extends ExpIdentifiableBaseImpl<ProtocolApplication> implements ExpProtocolApplication
{
    private List<ExpMaterialImpl> _inputMaterials;
    private List<ExpDataImpl> _inputDatas;
    private List<ExpMaterialImpl> _outputMaterials;
    private List<ExpDataImpl> _outputDatas;

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
    public List<ExpDataRunInputImpl> getDataInputs()
    {
        return ExpDataRunInputImpl.fromInputs(ExperimentServiceImpl.get().getDataInputsForApplication(getRowId()));
    }

    @Override
    @NotNull
    public List<ExpDataRunInputImpl> getDataOutputs()
    {
        return ExpDataRunInputImpl.fromInputs(ExperimentServiceImpl.get().getDataOutputsForApplication(getRowId()));
    }

    @NotNull
    public List<ExpDataImpl> getInputDatas()
    {
        if (_inputDatas == null)
        {
            List<ExpDataRunInputImpl> inputs = getDataInputs();
            _inputDatas= new ArrayList<>(inputs.size());
            for (ExpDataRunInputImpl input : inputs)
            {
                _inputDatas.add(input.getData());
            }
            Collections.sort(_inputDatas);
        }
        return _inputDatas;
    }

    @NotNull
    public List<ExpMaterialImpl> getInputMaterials()
    {
        if (_inputMaterials == null)
        {
            List<ExpMaterialRunInputImpl> inputs = getMaterialInputs();
            _inputMaterials = new ArrayList<>(inputs.size());
            for (ExpMaterialRunInputImpl input : inputs)
            {
                _inputMaterials.add(input.getMaterial());
            }
            Collections.sort(_inputMaterials);
        }
        return _inputMaterials;
    }

    @NotNull
    public List<ExpDataImpl> getOutputDatas()
    {
        if (_outputDatas == null)
        {
            _outputDatas = new ArrayList<>(ExpDataImpl.fromDatas(ExperimentServiceImpl.get().getOutputDataForApplication(getRowId())));
            Collections.sort(_outputDatas);
        }
        return _outputDatas;
    }

    @NotNull
    public List<ExpMaterialRunInputImpl> getMaterialInputs()
    {
        return ExpMaterialRunInputImpl.fromInputs(ExperimentServiceImpl.get().getMaterialInputsForApplication(getRowId()));
    }

    @Override
    @NotNull
    public List<ExpMaterialRunInputImpl> getMaterialOutputs()
    {
        return ExpMaterialRunInputImpl.fromInputs(ExperimentServiceImpl.get().getMaterialOutputsForApplication(getRowId()));
    }

    @NotNull
    public List<ExpMaterialImpl> getOutputMaterials()
    {
        if (_outputMaterials == null)
        {
            _outputMaterials = new ArrayList<>(ExpMaterialImpl.fromMaterials(ExperimentServiceImpl.get().getOutputMaterialForApplication(getRowId())));
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

    public void setStartTime(Date date)
    {
        ensureUnlocked();
        _object.setStartTime(date);
    }

    public void setEndTime(Date date)
    {
        ensureUnlocked();
        _object.setEndTime(date);
    }

    public void setRecordCount(Integer recordCount)
    {
        ensureUnlocked();
        _object.setRecordCount(recordCount);
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

    public Date getStartTime()
    {
        return _object.getStartTime();
    }

    public Date getEndTime()
    {
        return _object.getEndTime();
    }

    public Integer getRecordCount()
    {
        return _object.getRecordCount();
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
            final ExperimentServiceImpl svc = ExperimentServiceImpl.get();
            final SqlDialect dialect = svc.getSchema().getSqlDialect();

            // Clean up DataInput and MaterialInput exp.object and properties
            OntologyManager.deleteOntologyObjects(svc.getSchema(), new SQLFragment("SELECT " +
                    dialect.concatenate("'" + DataInput.lsidPrefix() + "'",
                            "CAST(dataId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                    " FROM " + svc.getTinfoDataInput() + " WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + getRowId() + ")"), getContainer(), false);

            OntologyManager.deleteOntologyObjects(svc.getSchema(), new SQLFragment("SELECT " +
                    dialect.concatenate("'" + MaterialInput.lsidPrefix() + "'",
                            "CAST(materialId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                    " FROM " + svc.getTinfoMaterialInput() + " WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + getRowId() + ")"), getContainer(), false);

            long countInputs = 0;
            countInputs += Table.delete(ExperimentServiceImpl.get().getTinfoDataInput(), new SimpleFilter(FieldKey.fromParts("TargetApplicationId"), getRowId()));
            countInputs += Table.delete(ExperimentServiceImpl.get().getTinfoMaterialInput(), new SimpleFilter(FieldKey.fromParts("TargetApplicationId"), getRowId()));
            if (countInputs > 0)
                ExperimentServiceImpl.get().uncacheLineageGraph();
            Table.delete(ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), new SimpleFilter(FieldKey.fromParts("ProtocolApplicationId"), getRowId()));

            SQLFragment commonSQL = new SQLFragment(" SET SourceApplicationId = NULL, RunId = NULL WHERE SourceApplicationId = ?", getRowId());

            SQLFragment materialSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoMaterial());
            materialSQL.append(commonSQL);
            new SqlExecutor(ExperimentServiceImpl.get().getSchema()).execute(materialSQL);

            SQLFragment dataSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoData());
            dataSQL.append(commonSQL);
            new SqlExecutor(ExperimentServiceImpl.get().getSchema()).execute(dataSQL);

            Table.delete(ExperimentServiceImpl.get().getTinfoProtocolApplication(), getRowId());
        }
    }

    public void setInputMaterials(List<ExpMaterialImpl> inputMaterialList)
    {
        ensureUnlocked();
        _inputMaterials = inputMaterialList;
    }

    public void setInputDatas(List<ExpDataImpl> inputDataList)
    {
        ensureUnlocked();
        _inputDatas = inputDataList;
    }

    public void setOutputMaterials(List<ExpMaterialImpl> outputMaterialList)
    {
        ensureUnlocked();
        _outputMaterials = outputMaterialList;
    }

    public void setOutputDatas(List<ExpDataImpl> outputDataList)
    {
        ensureUnlocked();
        _outputDatas = outputDataList;
    }

    @Override
    @NotNull
    public ExpDataRunInputImpl addDataInput(User user, ExpData data, String roleName)
    {
        DataInput obj = new DataInput();
        obj.setDataId(data.getRowId());
        obj.setTargetApplicationId(getRowId());
        obj.setRole(roleName);

        obj = Table.insert(user, ExperimentServiceImpl.get().getTinfoDataInput(), obj);
        ExperimentServiceImpl.get().uncacheLineageGraph();
        return new ExpDataRunInputImpl(obj);
    }

    @Override
    @NotNull
    public ExpMaterialRunInputImpl addMaterialInput(User user, ExpMaterial material, @Nullable String roleName)
    {
        MaterialInput obj = new MaterialInput();
        obj.setMaterialId(material.getRowId());
        obj.setTargetApplicationId(getRowId());
        obj.setRole(roleName);
        obj = Table.insert(user, ExperimentServiceImpl.get().getTinfoMaterialInput(), obj);
        ExperimentServiceImpl.get().uncacheLineageGraph();
        return new ExpMaterialRunInputImpl(obj);
    }

    @Override
    public void removeDataInput(User user, ExpData data)
    {
        // Clean up DataInput exp.object and properties
        String lsid = DataInput.lsid(data.getRowId(), getRowId());
        OntologyManager.deleteOntologyObjects(getContainer(), lsid);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("TargetApplicationId"), getRowId());
        filter.addCondition(FieldKey.fromParts("DataId"), data.getRowId());
        Table.delete(ExperimentServiceImpl.get().getTinfoDataInput(), filter);
        ExperimentServiceImpl.get().uncacheLineageGraph();
    }

    @Override
    public void removeMaterialInput(User user, ExpMaterial material)
    {
        // Clean up MaterialInput exp.object and properties
        String lsid = MaterialInput.lsid(material.getRowId(), getRowId());
        OntologyManager.deleteOntologyObjects(getContainer(), lsid);

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("TargetApplicationId"), getRowId());
        filter.addCondition(FieldKey.fromParts("MaterialId"), material.getRowId());
        Table.delete(ExperimentServiceImpl.get().getTinfoMaterialInput(), filter);
        ExperimentServiceImpl.get().uncacheLineageGraph();
    }

    public static List<ExpProtocolApplicationImpl> fromProtocolApplications(List<ProtocolApplication> apps)
    {
        List<ExpProtocolApplicationImpl> result = new ArrayList<>(apps.size());
        for (ProtocolApplication app : apps)
        {
            result.add(new ExpProtocolApplicationImpl(app));
        }
        return result;
    }

    public String toString()
    {
        return "ProtocolApplication, LSID: " + getLSID() + (getRun() == null ? null : ( ", run: " + getRun().getLSID()));  
    }
}
