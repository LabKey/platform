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

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.SampleWorkflowDeletePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.DotGraph;
import org.labkey.experiment.ExperimentRunGraph;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.experiment.api.ExperimentServiceImpl.getExpSchema;

public class ExpRunImpl extends ExpIdentifiableEntityImpl<ExperimentRun> implements ExpRun
{
    public static final String NAMESPACE_PREFIX = "Run";

    private boolean _populated;
    private List<ExpProtocolApplicationImpl> _protocolSteps;
    private Map<ExpMaterialImpl, String> _materialInputs = new HashMap<>();
    private Map<ExpDataImpl, String> _dataInputs = new HashMap<>();
    private List<ExpMaterial> _materialOutputs = new ArrayList<>();
    private List<ExpData> _dataOutputs = new ArrayList<>();
    private ExpRunImpl _replacedByRun;
    private Integer _maxOutputActionSequence = null;
    private static final Logger LOG = LogManager.getLogger(ExpRunImpl.class);
    private ExpProtocolApplication _workflowTask;

    static public List<ExpRunImpl> fromRuns(List<ExperimentRun> runs)
    {
        List<ExpRunImpl> ret = new ArrayList<>(runs.size());
        for (ExperimentRun run : runs)
        {
            ret.add(new ExpRunImpl(run));
        }
        return ret;
    }

    // For serialization
    protected ExpRunImpl() {}

    public ExpRunImpl(ExperimentRun run)
    {
        super(run);
        if (null != run && null != run.getObjectId())
            _objectId = run.getObjectId();
    }

    @Override
    public ActionURL detailsURL()
    {
        return _object.detailsURL();
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        ExpProtocol protocol = getProtocol();
        if (protocol != null)
        {
            ProtocolImplementation impl = protocol.getImplementation();
            if (impl != null)
            {
                QueryRowReference ref = impl.getQueryRowReference(protocol, this);
                if (ref != null)
                    return ref;
            }

            ExperimentRunType type = ExperimentService.get().getExperimentRunType(protocol);
            if (type != null)
            {
                QueryRowReference ref = type.getQueryRowReference(protocol, this);
                if (ref != null)
                    return ref;
            }
        }

        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Runs.name(), FieldKey.fromParts(ExpRunTable.Column.RowId), getRowId());
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public List<ExpExperimentImpl> getExperiments()
    {
        final String sql= " SELECT E.* FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " E "
                        + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoRunList() + " RL ON (E.RowId = RL.ExperimentId) "
                        + " INNER JOIN " + ExperimentServiceImpl.get().getTinfoExperimentRun() + " ER ON (ER.RowId = RL.ExperimentRunId) "
                        + " WHERE ER.LSID = ? AND E.Hidden = ?";

        return ExpExperimentImpl.fromExperiments(new SqlSelector(getExpSchema(), sql, _object.getLSID(), Boolean.FALSE).getArray(Experiment.class));
    }

    @Override
    public @Nullable ExpExperimentImpl getBatch()
    {
        if (_object.getBatchId() == null)
            return null;

        ExpExperimentImpl batch = ExperimentServiceImpl.get().getExpExperiment(_object.getBatchId());
        if (batch == null)
            return null;

        assert checkBatch(batch);
        return batch;
    }

    private boolean checkBatch(ExpExperimentImpl batch)
    {
        List<ExpExperimentImpl> exps = getExperiments();
        if (!exps.contains(batch))
        {
            LOG.warn("Expected batch '" + batch.getRowId() + "' to be in list of experiments: " + exps);
            return false;
        }

        if (!getProtocol().equals(batch.getBatchProtocol()))
        {
            LOG.warn("Expected batch '" + batch.getRowId() + "' to have same protocol as run.  Expected protocol '" + getProtocol() + "', but found '" + batch.getBatchProtocol() + "'");
            return false;
        }

        return true;
    }

    protected void setBatchId(Integer batchId)
    {
        assert batchId == null || checkBatch(ExperimentServiceImpl.get().getExpExperiment(batchId));
        _object.setBatchId(batchId);
    }

    @Override
    public ExpProtocolImpl getProtocol()
    {
        return ExperimentServiceImpl.get().getExpProtocol(_object.getProtocolLSID());
    }

    @Override
    public boolean isFinalOutput(ExpData data)
    {
        ExpProtocolApplication sourceApplication = data.getSourceApplication();
        return sourceApplication != null && sourceApplication.getActionSequence() == getMaxOutputActionSequence();
    }

    private int getMaxOutputActionSequence()
    {
        if (_maxOutputActionSequence == null)
        {
            _maxOutputActionSequence = 0;
            for (ExpProtocolApplication app : getProtocolApplications())
                if (!app.getDataOutputs().isEmpty() && app.getActionSequence() > _maxOutputActionSequence)
                    _maxOutputActionSequence = app.getActionSequence();
        }
        return _maxOutputActionSequence;
    }

    @Override
    public List<ExpDataImpl> getOutputDatas(@Nullable DataType type)
    {
        return ExperimentServiceImpl.get().getOutputDatas(getRowId(), type);
    }

    @Override
    public List<ExpDataImpl> getInputDatas(@Nullable String inputRole, @Nullable ExpProtocol.ApplicationType applicationType)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT exp.Data.* FROM exp.Data WHERE exp.Data.RowId IN ");
        sql.append("\n(SELECT exp.DataInput.DataId FROM exp.DataInput ");
        sql.append("\nINNER JOIN exp.ProtocolApplication ON exp.DataInput.TargetApplicationId = exp.ProtocolApplication.RowId");
        sql.append("\nWHERE exp.ProtocolApplication.RunId = ?");
        sql.add(getRowId());
        if (inputRole != null)
        {
            sql.append("\nAND exp.DataInput.Role = ? ");
            sql.add(inputRole);
        }
        if (applicationType != null)
        {
            sql.append("\nAND exp.ProtocolApplication.CpasType = ");
            sql.appendValue(applicationType.toString());
        }
        sql.append(")");
        return ExpDataImpl.fromDatas(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(Data.class));
    }

    @Override
    public File getFilePathRoot()
    {
        if (_object.getFilePathRoot() == null || FileUtil.hasCloudScheme(_object.getFilePathRoot()))
            return null;
        else
            return new File(FileUtil.createUri(_object.getFilePathRoot(), false));
    }

    @Override
    public void setFilePathRoot(File file)
    {
        ensureUnlocked();
        // We want "/Users/..." for local file system
        _object.setFilePathRoot(file == null ? null : file.toPath().toString());
    }

    @Override
    public Path getFilePathRootPath()
    {
        if (_object.getFilePathRoot() == null)
            return null;
        else if (FileUtil.hasCloudScheme(_object.getFilePathRoot()))
            return CloudStoreService.get().getPathFromUrl(getContainer(), _object.getFilePathRoot());
        else
            return new File(FileUtil.createUri(_object.getFilePathRoot(), false)).toPath();
    }

    @Override
    public void setFilePathRootPath(Path filePathRoot)
    {
        ensureUnlocked();
       _object.setFilePathRoot(filePathRoot == null ? null :
               FileUtil.hasCloudScheme(filePathRoot) ?
                       FileUtil.pathToString(filePathRoot) :
                       filePathRoot.toString());
    }

    @Override
    public void save(User user) throws BatchValidationException
    {
        ExperimentServiceImpl expService = ExperimentServiceImpl.get();
        try (DbScope.Transaction t = getExpSchema().getScope().ensureTransaction())
        {
            boolean newRun = getRowId() == 0;
            expService.onBeforeRunSaved(getProtocol(), this, getContainer(), user);
            save(user, expService.getTinfoExperimentRun(), true);
            if (newRun)
                expService.auditRunEvent(user, this.getProtocol(), this, null, this.getProtocol().getName() + " run loaded");
            t.addCommitTask(() -> expService.onAfterRunSaved(getProtocol(), this, getContainer(), user), DbScope.CommitTaskOption.POSTCOMMIT);
            t.commit();
        }
    }

    @Override
    protected void save(User user, TableInfo table, boolean ensureObject)
    {
        assert ensureObject;
        super.save(user, table, true);
        ExperimentServiceImpl.get().invalidateExperimentRun(getLSID());
    }

    @Override
    public void delete(User user)
    {
        delete(user, null);
    }

    @Override
    public void delete(User user, @Nullable final String auditUserComment)
    {
        if (!canDelete(user))
        {
            throw new UnauthorizedException();
        }
        ExperimentServiceImpl.get().deleteExperimentRuns(getContainer(), user, auditUserComment, Arrays.asList(this));
    }

    @Override
    public void setProtocol(ExpProtocol protocol)
    {
        _object.setProtocolLSID(protocol.getLSID());
    }

    @Override
    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType, String name)
    {
        return addProtocolApplication(user, action, appType, name, null, null, null);
    }

    public ExpProtocolApplicationImpl addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType appType, String name, java.util.Date startTime, java.util.Date endTime, Integer recordCount)
    {
        ensureUnlocked();
        ProtocolApplication pa = new ProtocolApplication();
        if (action == null)
        {
            if (appType != ExpProtocol.ApplicationType.ExperimentRun)
            {
                throw new IllegalArgumentException("Only the ExperimentRun protocol application type is allowed to have a null protocol action.");
            }
            pa.setProtocolLSID(getProtocol().getLSID());
        }
        else
        {
            pa.setProtocolLSID(action.getChildProtocol().getLSID());
            pa.setActionSequence(action.getActionSequence());
        }
        pa.setName(name);
        pa.setLSID(ExperimentService.get().generateGuidLSID(getContainer(), ExpProtocolApplication.class));
        pa.setCpasType(appType.toString());
        pa.setRunId(getRowId());
        pa.setStartTime(startTime);
        pa.setEndTime(endTime);
        pa.setRecordCount(recordCount);

        pa = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolApplication(), pa);
        return new ExpProtocolApplicationImpl(pa);
    }

    @Override
    public String getComments()
    {
        return _object.getComments();
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
    public String getEntityId()
    {
        return _object.getEntityId();
    }


    @Override
    public void setContainer(Container container)
    {
        _object.setContainer(container);
    }

    @Override
    public void setJobId(Integer jobId)
    {
        ensureUnlocked();
        _object.setJobId(jobId);
    }

    @Override
    public Integer getJobId()
    {
        return _object.getJobId();
    }

    public void setProtocolApplications(List<ExpProtocolApplicationImpl> protocolSteps)
    {
        ensureUnlocked();
        _protocolSteps = protocolSteps;
    }

    @Override
    public @NotNull Map<ExpMaterialImpl, String> getMaterialInputs()
    {
        ensureFullyPopulated();
        return _materialInputs;
    }

    @Override
    public @NotNull Map<ExpDataImpl, String> getDataInputs()
    {
        ensureFullyPopulated();
        return _dataInputs;
    }

    @Override
    public List<ExpMaterial> getMaterialOutputs()
    {
        ensureFullyPopulated();
        return _materialOutputs;
    }

    @Override
    public List<ExpData> getDataOutputs()
    {
        ensureFullyPopulated();
        return _dataOutputs;
    }

    @Override
    public List<ExpProtocolApplicationImpl> getProtocolApplications()
    {
        ensureFullyPopulated();
        return _protocolSteps;
    }

    @Override
    public @Nullable ExpProtocolApplication getProtocolApplication()
    {
        return findProtocolApplication(ExpProtocol.ApplicationType.ProtocolApplication);
    }

    @Override
    public @Nullable ExpProtocolApplication getInputProtocolApplication()
    {
        return findProtocolApplication(ExpProtocol.ApplicationType.ExperimentRun);
    }

    private @Nullable ExpProtocolApplication findProtocolApplication(ExpProtocol.ApplicationType type)
    {
        for (ExpProtocolApplicationImpl expProtocolApplication : getProtocolApplications())
        {
            if (type.equals(expProtocolApplication.getApplicationType()))
            {
                return expProtocolApplication;
            }
        }
        return null;
    }

    @Override
    public @Nullable ExpProtocolApplication getOutputProtocolApplication()
    {
        return findProtocolApplication(ExpProtocol.ApplicationType.ExperimentRunOutput);
    }

    @Override
    public void deleteProtocolApplications(User user)
    {
        ensureUnlocked();
        deleteProtocolApplications(getOutputDatas(null), user);
    }

    @Override
    public void setReplacedByRun(ExpRun run)
    {
        ensureUnlocked();
        if (run != null && run.getRowId() < 1)
        {
            throw new IllegalArgumentException("Run must have already been saved to the database");
        }
        _object.setReplacedByRunId(run == null ? null : run.getRowId());
    }

    @Override
    public ExpRun getReplacedByRun()
    {
        Integer id = _object.getReplacedByRunId();
        if (id == null)
        {
            return null;
        }
        if (_replacedByRun == null || _replacedByRun.getRowId() != id.intValue())
        {
            _replacedByRun = ExperimentServiceImpl.get().getExpRun(id.intValue());
        }
        return _replacedByRun;
    }

    @Override
    public List<ExpRunImpl> getReplacesRuns()
    {
        return fromRuns(new TableSelector(ExperimentServiceImpl.get().getTinfoExperimentRun(), new SimpleFilter(FieldKey.fromParts("ReplacedByRunId"), getRowId()), new Sort("Name")).getArrayList(ExperimentRun.class));
    }

    public void deleteProtocolApplications(List<ExpDataImpl> datasToDelete, User user)
    {
        if (user == null || !canDelete(user))
        {
            throw new UnauthorizedException("Attempting to delete an ExperimentRun without having delete permissions for its container");
        }
        final ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        final SqlDialect dialect = svc.getSchema().getSqlDialect();

        svc.invalidateExperimentRun(getLSID());

        deleteProtocolApplicationProvenance();

        svc.beforeDeleteData(user, getContainer(), datasToDelete);

        deleteInputObjects(svc, dialect);

        deleteAppParametersAndInputs();

        deleteRunMaterials(user);

        deleteRunProtocolApps();

        clearCache();

        // Clear the cache in a commit task, which allows us to do a single clear (which is semi-expensive) if multiple
        // runs are being deleted in the same transaction, like deleting a container
        svc.getSchema().getScope().addCommitTask(ExperimentRunGraph.getCacheClearingCommitTask(getContainer()), DbScope.CommitTaskOption.POSTCOMMIT);
    }

    @Override
    public boolean canDelete(User user)
    {
        ExpProtocolImpl protocol = getProtocol();
        boolean isWorkflow = ExpProtocol.isSampleWorkflowProtocol(protocol.getLSID());
        if (isWorkflow && getContainer().hasPermission(user, SampleWorkflowDeletePermission.class))
            return true;

        // Issue 50776: To update lineage we need to delete existing runs
        if ((ExperimentServiceImpl.get().isSampleAliquot(protocol) || ExperimentServiceImpl.get().isSampleDerivation(protocol)) && getContainer().hasPermission(user, UpdatePermission.class))
            return true;

        return !isWorkflow && getContainer().hasPermission(user, DeletePermission.class);
    }

    // Clean up DataInput and MaterialInput exp.object and properties
    private void deleteInputObjects(ExperimentServiceImpl svc, SqlDialect dialect)
    {
        SQLFragment materialSQL = new SQLFragment("SELECT " +
                dialect.concatenate("'" + DataInput.lsidPrefix() + "'",
                        "CAST(dataId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                " FROM " + svc.getTinfoDataInput() + " WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = ?)", getRowId());

        SQLFragment materialInputSQL = new SQLFragment("SELECT " +
                dialect.concatenate("'" + MaterialInput.lsidPrefix() + "'",
                        "CAST(materialId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                " FROM " + svc.getTinfoMaterialInput() + " WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = ?)", + getRowId());

        SQLFragment dataSQL = new SQLFragment("SELECT " +
                dialect.concatenate("'" + DataInput.lsidPrefix() + "'",
                        "CAST(dataId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                " FROM " + svc.getTinfoDataInput() + " WHERE DataId IN (SELECT RowId FROM exp.Data WHERE RunId = ?)", getRowId());

        SQLFragment dataInputSQL = new SQLFragment("SELECT " +
                dialect.concatenate("'" + MaterialInput.lsidPrefix() + "'",
                        "CAST(materialId AS VARCHAR)", "'.'", "CAST(targetApplicationId AS VARCHAR)") +
                " FROM " + svc.getTinfoMaterialInput() + " WHERE MaterialId IN (SELECT RowId FROM exp.Material WHERE RunId = ?)", getRowId());

        // Build a single UNION to help reduce DB round trips
        SQLFragment unionSQL = new SQLFragment("(")
                .append(materialSQL)
                .append("\n UNION \n")
                    .append(materialInputSQL)
                .append("\n UNION \n")
                .append(dataSQL)
                .append("\n UNION \n")
                .append(dataInputSQL)
                .append(")");

        OntologyManager.deleteOntologyObjects(svc.getSchema(), unionSQL, getContainer());
    }

    private void deleteProtocolApplicationProvenance()
    {
        ProvenanceService pvs = ProvenanceService.get();
        pvs.deleteRunProvenance(getRowId());
    }

    private void deleteAppParametersAndInputs()
    {
        SQLFragment sql = new SQLFragment();
        sql.append("DELETE FROM exp.ProtocolApplicationParameter WHERE ProtocolApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = ?)").add(getRowId()).appendEOS();

        //per Josh: break relation between all datas with this run id and don't delete them
        sql.append("UPDATE exp.Data SET SourceApplicationId = NULL, RunId = NULL WHERE RunId = ?").add(getRowId()).appendEOS();
        sql.append("UPDATE exp.Material SET SourceApplicationId = NULL, RunId = NULL WHERE RunId = ?").add(getRowId()).appendEOS();

        sql.append("DELETE FROM exp.DataInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = ?)").add(getRowId()).appendEOS();
        sql.append("DELETE FROM exp.MaterialInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = ?)").add(getRowId()).appendEOS();
        sql.append("DELETE FROM exp.DataInput WHERE DataId IN (SELECT RowId FROM exp.Data WHERE RunId = ?)").add(getRowId()).appendEOS();
        sql.append("DELETE FROM exp.MaterialInput WHERE MaterialId IN (SELECT RowId FROM exp.Material WHERE RunId = ?)").add(getRowId()).appendEOS();

        new SqlExecutor(getExpSchema()).execute(sql);
    }

    private void deleteRunMaterials(User user)
    {
        for (ExpMaterial expMaterial : ExperimentServiceImpl.get().getExpMaterialsForRun(getRowId()))
        {
            expMaterial.delete(user);
        }
    }

    private void deleteRunProtocolApps()
    {
        new SqlExecutor(getExpSchema()).execute("DELETE FROM exp.ProtocolApplication WHERE RunId = ?", getRowId());
    }

    private synchronized void ensureFullyPopulated()
    {
        if (!_populated)
        {
            _populated = true;
            ExperimentServiceImpl.get().populateRun(this);
            Collections.sort(_materialOutputs);
            Collections.sort(_dataOutputs);

            for (ExpProtocolApplicationImpl step : _protocolSteps)
            {
                Collections.sort(step.getInputDatas());
                Collections.sort(step.getOutputDatas());
                Collections.sort(step.getInputMaterials());
                Collections.sort(step.getOutputMaterials());
            }
        }
    }

    public void trimRunTree(Integer id, String type) throws ExperimentException
    {
        ensureUnlocked();
        List<ExpProtocolApplication> listPA = new ArrayList<>();
        List<ExpMaterialImpl> listM = new ArrayList<>();
        List<ExpData> listD = new ArrayList<>();
        Set<ExpProtocolApplicationImpl> ancestorPAStack = new LinkedHashSet<>();
        Set<ExpProtocolApplicationImpl> descendantPAStack = new LinkedHashSet<>();
        List<ExpProtocolApplicationImpl> apps = getProtocolApplications();

        boolean found = false;

        // support focus on a starting material that is not part of the run
        if (type == null || DotGraph.TYPECODE_MATERIAL.equalsIgnoreCase(type))
        {
            for (ExpMaterialImpl m : getMaterialInputs().keySet())
                if (m.getRowId() == id.intValue())
                {
                    found = true;
                    listM.add(m);
                    listPA.addAll(m.getSuccessorApps());
                    descendantPAStack.addAll(m.getSuccessorApps());
                    break;
                }
        }
        if (type == null || DotGraph.TYPECODE_DATA.equalsIgnoreCase(type))
        {
            for (ExpDataImpl d : getDataInputs().keySet())
                if (d.getRowId() == id.intValue())
                {
                    found = true;
                    listD.add(d);
                    listPA.addAll(d.getSuccessorApps());
                    descendantPAStack.addAll(d.getSuccessorApps());
                    break;
                }
        }
        if (!found)
        {
            for (ExpProtocolApplicationImpl app : apps)
            {
                if (type == null || DotGraph.TYPECODE_MATERIAL.equalsIgnoreCase(type))
                {
                    for (ExpMaterialImpl m : app.getOutputMaterials())
                        if (m.getRowId() == id.intValue())
                        {
                            found = true;
                            listM.add(m);
                            listPA.addAll(m.getSuccessorApps());
                            descendantPAStack.addAll(m.getSuccessorApps());
                            if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                            {
                                listPA.add(m.getSourceApplication());
                                ancestorPAStack.add(m.getSourceApplication());
                            }
                            break;
                        }
                }
                if (type == null || DotGraph.TYPECODE_DATA.equalsIgnoreCase(type))
                {
                    for (ExpDataImpl d : app.getOutputDatas())
                    {
                        if (d.getRowId() == id.intValue())
                        {
                            found = true;
                            listD.add(d);
                            listPA.addAll(d.getSuccessorApps());
                            descendantPAStack.addAll(d.getSuccessorApps());
                            if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                            {
                                listPA.add(d.getSourceApplication());
                                ancestorPAStack.add(d.getSourceApplication());
                            }
                            break;
                        }
                    }
                }
                if (type == null || DotGraph.TYPECODE_PROT_APP.equalsIgnoreCase(type))
                {
                    if (app.getRowId() == id.intValue())
                    {
                        found = true;
                        listPA.add(app);
                        ancestorPAStack.add(app);
                        descendantPAStack.add(app);
                        break;
                    }
                }
                if (found)
                    break;
            }
        }
        if (!found)
            throw new ExperimentException("Specified node not found in Experiment Run");

        int loopCount = 0;
        while (!descendantPAStack.isEmpty())
        {
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }

            ExpProtocolApplicationImpl pa = descendantPAStack.iterator().next();
            for (ExpMaterialImpl m : pa.getOutputMaterials())
            {
                listM.add(m);
                descendantPAStack.addAll(m.getSuccessorApps());
            }
            for (ExpDataImpl d : pa.getOutputDatas())
            {
                listD.add(d);
                descendantPAStack.addAll(d.getSuccessorApps());
            }
            descendantPAStack.remove(pa);
            listPA.add(pa);
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }
        }

        loopCount = 0;
        while (!ancestorPAStack.isEmpty())
        {
            if (loopCount++ > 10000)
            {
                throw new IllegalStateException("Infinite loop detected for run " + getRowId());
            }

            ExpProtocolApplicationImpl pa = ancestorPAStack.iterator().next();
            if (pa.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun)
                break;
            for (ExpMaterialImpl m : pa.getInputMaterials())
            {
                listM.add(m);
                if (getMaterialInputs().containsKey(m))
                {
                    ExpProtocolApplication runNode = getProtocolApplications().get(0);
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != m.getSourceApplication() && m.getRun() != null && getRowId() == m.getRun().getRowId())
                    ancestorPAStack.add(m.getSourceApplication());
            }
            for (ExpDataImpl d : pa.getInputDatas())
            {
                listD.add(d);
                if (getDataInputs().containsKey(d))
                {
                    ExpProtocolApplication runNode = getProtocolApplications().get(0);
                    assert runNode.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun;
                    listPA.add(runNode);
                    continue;
                }
                if (null != d.getSourceApplication() && d.getRun() != null && getRowId() == d.getRun().getRowId())
                    ancestorPAStack.add(d.getSourceApplication());
            }
            ancestorPAStack.remove(pa);
            listPA.add(pa);
        }

        List<ExpProtocolApplicationImpl> allPA = new ArrayList<>();
        List<ExpProtocolApplicationImpl> deletePA;
        List<ExpMaterialImpl> deleteM;
        List<ExpDataImpl> deleteD;

        setProtocolApplications(null);

        for (ExpProtocolApplicationImpl app : apps)
        {
            if (listPA.contains(app))
            {
                allPA.add(app);
                deleteM = new ArrayList<>();
                for (ExpMaterialImpl m : app.getInputMaterials())
                {
                    if (listM.contains(m))
                    {
                        deletePA = new ArrayList<>();
                        for (ExpProtocolApplicationImpl p : m.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplicationImpl p : deletePA)
                            m.getSuccessorApps().remove(p);
                    }
                    else
                        deleteM.add(m);
                }
                for (ExpMaterialImpl m : deleteM)
                {
                    app.getInputMaterials().remove(m);
                    getMaterialInputs().remove(m);
                }

                deleteD = new ArrayList<>();
                for (ExpDataImpl d : app.getInputDatas())
                {
                    if (listD.contains(d))
                    {
                        deletePA = new ArrayList<>();
                        for (ExpProtocolApplicationImpl p : d.getSuccessorApps())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplicationImpl p : deletePA)
                            d.getSuccessorApps().remove(p);
                    }
                    else
                        deleteD.add(d);
                }
                for (ExpDataImpl d : deleteD)
                {
                    app.getInputDatas().remove(d);
                    getDataInputs().remove(d);
                }

                deleteM = new ArrayList<>();
                for (ExpMaterialImpl m : app.getOutputMaterials())
                {
                    if (!listM.contains(m))
                        deleteM.add(m);
                }
                for (ExpMaterialImpl m : deleteM)
                    app.getOutputMaterials().remove(m);


                deleteD = new ArrayList<>();
                for (ExpDataImpl d : app.getOutputDatas())
                {
                    if (!listD.contains(d))
                        deleteD.add(d);
                }
                for (ExpDataImpl d : deleteD)
                    app.getOutputDatas().remove(d);
            }
        }
        setProtocolApplications(allPA);
    }

    @Override
    public List<ExpDataImpl> getAllDataUsedByRun()
    {
        SQLFragment sql = new SQLFragment();
        sql.append("select d.* from ");
        sql.append(ExperimentServiceImpl.get().getTinfoDataInput(), "di");
        sql.append(", ");
        sql.append(ExperimentServiceImpl.get().getTinfoData(), "d");
        sql.append(", ");
        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sql.append(" where di.targetapplicationid=pa.rowid and pa.runid=? and di.dataid=d.rowid");
        sql.add(getRowId());
        return ExpDataImpl.fromDatas(new SqlSelector(ExperimentServiceImpl.get().getSchema(), sql).getArrayList(Data.class));
    }

    public void clearCache()
    {
        _populated = false;
        _dataInputs = new HashMap<>();
        _materialInputs = new HashMap<>();
        _materialOutputs = new ArrayList<>();
        _dataOutputs = new ArrayList<>();
        _protocolSteps = null;
    }

    @Override
    public void archiveDataFiles(User user)
    {
        try
        {
            PipeRoot pipeRoot = PipelineService.get().getPipelineRootSetting(getContainer());
            if (pipeRoot != null && pipeRoot.isValid())
            {
                for (ExpData expData : getAllDataUsedByRun())
                {
                    File file = expData.getFile();
                    // If we can find the file on disk, and it's in the assaydata directory or was created by this run,
                    // and there aren't any other usages, move it into an archived subdirectory.
                    if (NetworkDrive.exists(file) && file.isFile() &&
                            (inAssayData(file) || isGeneratedByRun(expData)) &&
                            !hasOtherRunUsing(expData, this))
                    {
                        File archivedDir;
                        try
                        {
                            archivedDir = AssayFileWriter.ensureSubdirectory(getContainer(), AssayFileWriter.ARCHIVED_DIR_NAME).toNioPathForWrite().toFile();
                        }
                        catch (ExperimentException e)
                        {
                            // In this case, the archived directory was not created correctly perhaps due to file permission or
                            // some other problem. This exception has been observed on production servers. The error is being
                            // ignored and the file is not archived. Archiving is a best attempt action.
                            LOG.warn("Unable to create an archive directory - discontinue archive and delete.");
                            return;
                        }
                        File targetFile = AssayFileWriter.findUniqueFileName(file.getName(), archivedDir);
                        targetFile = FileUtil.getAbsoluteCaseSensitiveFile(targetFile);
                        FileUtils.moveFile(file, targetFile);
                        expData.setDataFileURI(targetFile.toURI());
                        expData.save(user);
                    }
                }
            }
        }
        // Warn if the move fails for some reason (file is open / file deleted during archive)
        catch (IOException e)
        {
            LOG.warn("Unable to archive file:  " + e.getMessage());
        }
        // Fail silently if the parent directory does not exist - archiving is a best effort action
        catch (ExperimentException e)
        {
            LOG.warn("Unable to read parent directory: " + e.getMessage());
        }
    }

    private boolean inAssayData(File file) throws ExperimentException
    {
        var uploadDir = AssayFileWriter.ensureUploadDirectory(getContainer()).toNioPathForWrite().toFile();
        return file.getParentFile().equals(uploadDir);
    }

    /**
     * Returns true if the ExpData is flagged as being generated by a run
     * and the ExpData's source protocol application references this run.
     */
    private boolean isGeneratedByRun(ExpData data)
    {
        // TODO: is using data.getRun() sufficient or must we check sourceApp.getRun() to see if the data was created by this run?
        ExpProtocolApplication sourceApp = data.getSourceApplication();
        if (data.isGenerated() && sourceApp != null && this.equals(sourceApp.getRun()))
            return true;

        return false;
    }

    @Override
    public void setCreated(Date created)
    {
        _object.setCreated(created);
    }

    @Override
    public void setCreatedBy(User user)
    {
        _object.setCreatedBy(user == null ? 0 : user.getUserId());
    }

    private boolean hasOtherRunUsing(ExpData originalData, ExpRun originalRun)
    {
        if (originalData.getDataFileUrl() != null)
        {
            for (ExpData data : ExperimentService.get().getAllExpDataByURL(originalData.getDataFileUrl(), null))
            {
                for (ExpRun run : data.getTargetRuns())
                {
                    if (!run.equals(originalRun))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void setWorkflowTaskId(@Nullable Integer workflowTaskId)
    {
        _object.setWorkflowTask(workflowTaskId);
    }

    @Override
    public ExpProtocolApplication getWorkflowTask()
    {
        Integer id = _object.getWorkflowTask();

        if (id == null) {
            return null;
        }

        if (_workflowTask == null || _workflowTask.getRowId() != id.intValue())
        {
            _workflowTask = ExperimentServiceImpl.get().getExpProtocolApplication(id);
        }

        return _workflowTask;
    }

    @Override
    public void setWorkflowTask(ExpProtocolApplication workflowTask)
    {
        ensureUnlocked();

        if (workflowTask == null)
            _object.setWorkflowTask(null);
        else
            _object.setWorkflowTask(workflowTask.getRowId());
    }
}
