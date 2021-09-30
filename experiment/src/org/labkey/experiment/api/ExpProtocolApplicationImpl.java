/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpProtocolApplicationImpl extends ExpIdentifiableEntityImpl<ProtocolApplication> implements ExpProtocolApplication
{
    private List<ExpMaterialImpl> _inputMaterials;
    private List<ExpDataImpl> _inputDatas;
    private List<ExpMaterialImpl> _outputMaterials;
    private List<ExpDataImpl> _outputDatas;
    private Map<String, String> _lsidMap;

    // For serialization
    protected ExpProtocolApplicationImpl() {}

    public ExpProtocolApplicationImpl(ProtocolApplication app)
    {
        super(app);
    }

    @Override
    public ActionURL detailsURL()
    {
        return null;
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        ExpProtocolImpl protocol = getProtocol();
        if (protocol != null)
        {
            QueryRowReference ref = protocol.getCustomQueryRowReference();
            if (ref != null)
                return ref;
        }

        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.ProtocolApplications.name(), FieldKey.fromParts(ExpProtocolApplicationTable.Column.RowId), getRowId());
    }

    @Override
    public Date getCreated()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getCreated();
    }

    @Override
    public User getCreatedBy()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getCreatedBy();
    }

    @Override
    public User getModifiedBy()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getModifiedBy();
    }

    @Override
    public Date getModified()
    {
        ExpRun run = getRun();
        return null == run ? null : run.getModified();
    }

    @Override
    public Container getContainer()
    {
        return getRun().getContainer();
    }
    
    @Override
    public void setContainer(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public ExpProtocolImpl getProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolLSID());
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public ExpRunImpl getRun()
    {
        Integer runId = _object.getRunId();
        return runId == null ? null : ExperimentServiceImpl.get().getExpRun(runId.intValue());
    }

    @Override
    public void setRun(ExpRun run)
    {
        ensureUnlocked();
        _object.setRunId(run.getRowId());
    }

    @Override
    public void setActionSequence(int actionSequence)
    {
        ensureUnlocked();
        _object.setActionSequence(actionSequence);
    }

    @Override
    public void setProtocol(ExpProtocol protocol)
    {
        ensureUnlocked();
        _object.setProtocolLSID(protocol.getLSID());
        _object.setCpasType(protocol.getApplicationType().toString());
    }

    @Override
    public void setActivityDate(Date date)
    {
        ensureUnlocked();
        _object.setActivityDate(date);
    }

    @Override
    public void setStartTime(Date date)
    {
        ensureUnlocked();
        _object.setStartTime(date);
    }

    @Override
    public void setEndTime(Date date)
    {
        ensureUnlocked();
        _object.setEndTime(date);
    }

    @Override
    public void setRecordCount(Integer recordCount)
    {
        ensureUnlocked();
        _object.setRecordCount(recordCount);
    }

    @Override
    public void setComments(String comments)
    {
        ensureUnlocked();
        _object.setComments(comments);
    }

    @Override
    public void setEntityId(String entityId)
    {
        ensureUnlocked();
        _object.setEntityId(entityId);
    }

    @Override
    public int getActionSequence()
    {
        return _object.getActionSequence();
    }

    @Override
    public ExpProtocol.ApplicationType getApplicationType()
    {
        return ExpProtocol.ApplicationType.valueOf(_object.getCpasType());
    }

    @Override
    public Date getActivityDate()
    {
        return _object.getActivityDate();
    }

    @Override
    public Date getStartTime()
    {
        return _object.getStartTime();
    }

    @Override
    public Date getEndTime()
    {
        return _object.getEndTime();
    }

    @Override
    public Integer getRecordCount()
    {
        return _object.getRecordCount();
    }

    @Override
    public String getComments()
    {
        return _object.getComments();
    }

    @Override
    public String getEntityId()
    {
        return _object.getEntityId();
    }

    @Override
    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), false);
    }

    @Override
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
                ExperimentServiceImpl.get().queueSyncRunEdges(_object.getRunId());
            Table.delete(ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), new SimpleFilter(FieldKey.fromParts("ProtocolApplicationId"), getRowId()));

            SQLFragment commonSQL = new SQLFragment(" SET SourceApplicationId = NULL, RunId = NULL WHERE SourceApplicationId = ?", getRowId());

            SQLFragment materialSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoMaterial());
            materialSQL.append(commonSQL);
            new SqlExecutor(ExperimentServiceImpl.get().getSchema()).execute(materialSQL);

            SQLFragment dataSQL = new SQLFragment("UPDATE " + ExperimentServiceImpl.get().getTinfoData());
            dataSQL.append(commonSQL);
            new SqlExecutor(ExperimentServiceImpl.get().getSchema()).execute(dataSQL);

            ProvenanceService pvs = ProvenanceService.get();
            pvs.deleteProvenance(getRowId());

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
        return addDataInput(user, data, roleName, null);
    }

    @Override
    @NotNull
    public ExpDataRunInputImpl addDataInput(User user, ExpData data, String roleName, @Nullable ExpDataProtocolInput protocolInput)
    {
        DataInput obj = new DataInput();
        obj.setDataId(data.getRowId());
        obj.setTargetApplicationId(getRowId());
        obj.setRole(roleName);
        if (protocolInput != null)
        {
            if (!getProtocol().equals(protocolInput.getProtocol()))
                throw new IllegalArgumentException("protocol input must be associated with the same protocol as this protocol application");
            obj.setProtocolInputId(protocolInput.getRowId());
        }

        obj = Table.insert(user, ExperimentServiceImpl.get().getTinfoDataInput(), obj);
        ExperimentServiceImpl.get().queueSyncRunEdges(_object.getRunId());
        return new ExpDataRunInputImpl(obj);
    }

    @Override
    @NotNull
    public ExpMaterialRunInputImpl addMaterialInput(User user, ExpMaterial material, @Nullable String roleName)
    {
        return addMaterialInput(user, material, roleName, null);
    }

    @Override
    @NotNull
    public ExpMaterialRunInputImpl addMaterialInput(User user, ExpMaterial material, @Nullable String roleName, @Nullable ExpMaterialProtocolInput protocolInput)
    {
        MaterialInput obj = new MaterialInput();
        obj.setMaterialId(material.getRowId());
        obj.setTargetApplicationId(getRowId());
        obj.setRole(roleName);
        if (protocolInput != null)
        {
            if (!getProtocol().equals(protocolInput.getProtocol()))
                throw new IllegalArgumentException("protocol input must be associated with the same protocol as this protocol application");
            obj.setProtocolInputId(protocolInput.getRowId());
        }

        obj = Table.insert(user, ExperimentServiceImpl.get().getTinfoMaterialInput(), obj);
        ExperimentServiceImpl.get().queueSyncRunEdges(_object.getRunId());
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
        ExperimentServiceImpl.get().queueSyncRunEdges(_object.getRunId());
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
        ExperimentServiceImpl.get().queueSyncRunEdges(_object.getRunId());
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
        return "ProtocolApplication: " +
                "Name: " + getName() +
                ", Sequence: " + getActionSequence() +
                ", LSID: " + getLSID() + (getRun() == null ? null : ( ", run: " + getRun().getLSID()));
    }

    @Override
    public void addProvenanceInput(Set<String> lsids)
    {
        ProvenanceService pvs = ProvenanceService.get();
        if (!lsids.isEmpty())
        {
            pvs.addProvenanceInputs(this.getContainer(), this, lsids);
        }
    }

    @Override
    public void addProvenanceMapping(Set<Pair<String, String>> lsidPairs)
    {
        ProvenanceService pvs = ProvenanceService.get();
        if (!lsidPairs.isEmpty())
        {
            pvs.addProvenance(this.getContainer(), this, lsidPairs);
        }
    }

    @Override
    public Set<Pair<String, String>> getProvenanceMapping()
    {
        ProvenanceService pvs = ProvenanceService.get();
        return pvs.getProvenanceObjectUris(getRowId());
    }
}
