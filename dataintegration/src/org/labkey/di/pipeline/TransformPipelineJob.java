/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PropertiesJobSupport;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.data.TransformProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformPipelineJob extends PipelineJob implements TransformJobSupport, PropertiesJobSupport, FileAnalysisJobSupport
{
    static final String ETL_PREFIX = "ETL Job: ";
    private static final String LOG_EXTENSION = "etl.log";

    private final TransformDescriptor _etlDescriptor;
    private final Map<String,VariableMapImpl> _stepVariableMaps = new HashMap<>();
    private final Set<String> _outputFileBaseNames = new LinkedHashSet<>();
    private final VariableMapImpl _variableMap = new VariableMapImpl(null);

    private int _runId;
    private Integer _recordCount;
    private TransformJobContext _transformJobContext;

    private transient DbScope.Transaction _sourceTx;
    private transient DbScope.Transaction _targetTx;

    public TransformPipelineJob(@NotNull TransformJobContext info, TransformDescriptor etlDescriptor)
    {
        super(ETLPipelineProvider.NAME,
                new ViewBackgroundInfo(info.getContainer(), info.getUser(), null),
                PipelineService.get().findPipelineRoot(info.getContainer()));
        _etlDescriptor = etlDescriptor;

        File etlLogDir = getPipeRoot().resolvePath("etlLogs");
        //TODO: The string replace is a temp workaround until we remove the braces from the module name and switch
        // to passing around a TaskId instead.
        String filePath = StringUtils.replace(StringUtils.replace(etlDescriptor.getId(), "{", ""), "}", "");
        File etlLogFile = new File(etlLogDir, FileUtil.makeFileNameWithTimestamp(filePath, LOG_EXTENSION));
        _transformJobContext = new TransformJobContext(etlDescriptor, info.getContainer(), info.getUser(), info.getParams());
        _transformJobContext.setIncrementalWindow(info.getIncrementalWindow());
        setLogFile(etlLogFile);
        // Default job to etl log directory & file base name. These will be changed if etl target is a file.
        setAnalysisDirectory(etlLogDir);
        setBaseName(StringUtils.substringBefore(etlLogFile.getName(), "." + LOG_EXTENSION));
        initVariableMap(info);
        _parameters.putAll(etlDescriptor.getPipelineParameters());
    }

    private void initVariableMap(TransformJobContext info)
    {
        JSONObject savedState = TransformManager.get().getTransformConfiguration(info.getContainer(), info.getJobDescriptor()).getJsonState();
        if (!savedState.isEmpty())
        {
            for (Map.Entry<String, Object> e : savedState.entrySet())
            {
                _variableMap.put(e.getKey(), e.getValue());
            }
            JSONObject steps = (JSONObject)_variableMap.get("steps");
            if (!steps.isEmpty())
            {
                for (String k : steps.keySet())
                {
                    _stepVariableMaps.put(k, new VariableMapImpl(_variableMap, steps.getJSONObject(k)));
                }
            }
        }

        Map<ParameterDescription,Object> declaredVariables = _etlDescriptor.getDeclaredVariables();
        for (Map.Entry<ParameterDescription,Object> entry : declaredVariables.entrySet())
        {
            ParameterDescription pd = entry.getKey();
            Object value = entry.getValue();
            if (info.getParams().containsKey(pd))
                value = info.getParams().get(pd);
            _variableMap.put(pd,value);
        }

        if (!_etlDescriptor.getConstants().isEmpty())
        {
            Map<String, Object> persistConstants = _etlDescriptor.getConstants().entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue));
            _variableMap.put(TransformProperty.GlobalConstants, persistConstants);
        }
        if (!_etlDescriptor.getPipelineParameters().isEmpty())
        {
            _variableMap.put(TransformProperty.PipelineParameters, _etlDescriptor.getPipelineParameters());
        }

        _variableMap.put(TransformProperty.RanStep1, false, VariableMap.Scope.global);
    }

    public VariableMap getVariableMap()
    {
        return _variableMap;
    }


    public VariableMap getStepVariableMap(String id)
    {
        VariableMapImpl vm = _stepVariableMaps.get(id);
        if (null == vm)
            _stepVariableMaps.put(id, vm = new VariableMapImpl(_variableMap));
        return vm;
    }


    public void logRunFinish(TaskStatus status, Integer recordCount)
    {
        TransformRun run = getTransformRun();
        if (run != null)
        {
            // Mark that the job has finished successfully
            run.setStatus(status.toString());
            run.setEndTime(new Date());
            run.setRecordCount(recordCount);
            update(run);
        }

        if (TaskStatus.complete == status)
        {
            saveVariableMap(true);
        }
    }

    void saveVariableMap(boolean removeTransientProperties)
    {
        JSONObject state = _variableMap.toJSONObject();
        if (removeTransientProperties)
            state.remove(TransformProperty.RanStep1);
        JSONObject steps = new JSONObject();
        for (Map.Entry<String,VariableMapImpl> e : _stepVariableMaps.entrySet())
        {
            JSONObject step = e.getValue().toJSONObject();
            if (null == step || step.isEmpty())
                continue;
            steps.put(e.getKey(), step);
        }
        state.put("steps",steps);
        TransformConfiguration cfg = TransformManager.get().getTransformConfiguration(getContainer(),_etlDescriptor);
        cfg.setJsonState(state);
        TransformManager.get().saveTransformConfiguration(getUser(), cfg);
    }

    private static final DbScope.TransactionKind SNAPSHOT_TRANSACTION_KIND = new DbScope.TransactionKind()
    {
        @Override
        public @NotNull String getKind()
        {
            return "SNAPSHOT";
        }

        @Override
        public boolean isReleaseLocksOnFinalCommit()
        {
            return true;
        }
    };

    @Override
    public void run()
    {
        DbScope sourceScope = null;
        DbScope targetScope = null;

        /*
            ETL xmls may optionally be configured to wrap an entire multi-step ETL job in a transaction, on the source, destination, or both.
            The transaction on the source scope will be a REPEATABLE READ/SNAPSHOT transaction guaranteeing consistent state of source data
            throughout the transaction. This prevents phantom reads of new source data which has been added/updates since
            the ETL started.

            The transaction on the destination scope allows for rolling back the results an entire job in the event of a failure. This should
            be used with caution, as it can/will cause locking contention for the duration of the ETL job on tables
            the ETL has touched, especially on SQL Server.

            Additional caveats:
                - Specifying to transact the source and/or destination is only meaningful if every step in the ETL uses
                  the same source or destination scope. Different schemas are fine, as long as they are in the same DbScope
                - Transacting the source is only available for Postgres data sources. SQL Server is a future possibility.
                - If the source and destination schemas are in the same scope, a single REPEATABLE READ transaction is used. This
                  this is only available for Postgres.
         */
        try
        {
            if (null != _etlDescriptor.getTransactSourceSchema())
            {
                sourceScope = Optional.ofNullable(DefaultSchema
                        .get(_transformJobContext.getUser(), _transformJobContext.getContainer(), _etlDescriptor.getTransactSourceSchema()))
                        .orElseThrow(() -> new ConfigurationException("transactSourceSchema not found for this container: " + _etlDescriptor.getTransactSourceSchema()))
                        .getDbSchema()
                        .getScope();
            /*
            TODO: Support SQL Server if possible. Tricky:
              - On SQL Server, REPEATABLE READ isolation causes locking issues on the source tables. This defeats the purpose of using a transaction
                when sourcing from a live data source. To eliminate chance of the source transaction locking other activity, we'd need to use the
                SNAPSHOT isolation level instead. (this is essentially the equivalent of Postgres REPEATABLE READ, which exceeds the ANSI SQL standard for REPEATABLE READ isolation level)
              - Using SNAPSHOT isolation requires setting the database property ALLOW_SNAPSHOT_ISOLATION ON. This has impact for performance and db/tempdb size that need to be better understood
              - Changing the isolation level on SQL Server requires first acquiring a connection and setting the isolation level BEFORE beginning a transaction
                on that connection. (On Postgres the isolation level is set immediately AFTER beginning the transaction.) In order to still use DbScope.Transaction,
                 DbScope.beginTransaction() would need to allow optionally passing in this previously acquired & configured connection instead of grabbing one from the pool.
              - Note that while java.sql.Connection doesn't define a constant corresponding to SNAPSHOT isolation level, JTDS represents it as int 4096. (Or an explicit SET TRANSACTION
                  statement can be run over the connection.)
            */
                if (!sourceScope.getSqlDialect().isPostgreSQL())
                    throw new ConfigurationException("Transacting the source scope is only available on Postgres data sources.");
            }

            if (null != _etlDescriptor.getTransactTargetSchema())
            {
                targetScope = Optional.ofNullable(DefaultSchema
                        .get(_transformJobContext.getUser(), _transformJobContext.getContainer(), _etlDescriptor.getTransactTargetSchema()))
                        .orElseThrow(() -> new ConfigurationException("transactDestinationSchema not found for this container: " + _etlDescriptor.getTransactTargetSchema()))
                        .getDbSchema()
                        .getScope();

                if (targetScope.equals(sourceScope))
                    targetScope = null; // If both source and destination are on the same data source, we only have one connection/transaction
            }
        }
        catch (ConfigurationException e)
        {
            error("Configuration problem with ETL", e);
            return;
        }

        _sourceTx = null == sourceScope ? null : sourceScope.beginTransaction(SNAPSHOT_TRANSACTION_KIND);
        _targetTx = null == targetScope ? null : targetScope.beginTransaction();
        boolean rollbackSource = true;
        try
        {
            try
            {
                if (null != _sourceTx)
                {
                    _sourceTx.getConnection().setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                }
                super.run();
                if (TaskStatus.complete == this.getActiveTaskStatus())
                {
                    if (null != _sourceTx && !_sourceTx.isAborted())
                    {
                        _sourceTx.commitAndKeepConnection(); // Keep connection open so we can reset its isolation level to default
                        rollbackSource = false;
                    }
                    if (null != _targetTx && !_targetTx.isAborted())
                        _targetTx.commit();
                }
            }
            finally
            {
                closeWrappingTransactions(rollbackSource, false);
            }
        }
        catch (SQLException e)
        {
            getLogger().error("SQLException setting connection transaction isolation level.", e);
            _sourceTx.close();
        }
    }

    public void closeWrappingTransactions(boolean rollbackSource, boolean rollbackTarget)
    {
        if (null != _targetTx && !_targetTx.isAborted())
        {
            _targetTx.close();
            if (rollbackTarget)
            {
                getLogger().error("Rolling back wrapping destination transaction for all steps in ETL.");
            }
        }

        if (null != _sourceTx && !_sourceTx.isAborted())
        {
            try
            {
                // Need to explicitly end the transaction to be able to keep connection and set connection's isolation level back to default.
                if (rollbackSource)
                {
                    _sourceTx.getConnection().rollback();
                    getLogger().error("Rolling back wrapping source transaction for all steps in ETL.");
                }
                else
                    _sourceTx.getConnection().commit();
                _sourceTx.getConnection().setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }
            catch (SQLException e)
            {
                getLogger().error("SQLException rolling back wrapping source transaction or resetting connection transaction isolation level.", e);
            }
            finally
            {
                _sourceTx.close();
            }
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        super.cancel(mayInterruptIfRunning);
        logRunFinish(TaskStatus.cancelled, null);
        return true;
    }

    @Override
    public void done(Throwable throwable)
    {
        super.done(throwable);

        TaskStatus status = TaskStatus.complete;

        if (this.isCancelled() || TaskStatus.cancelled.equals(this.getActiveTaskStatus()))
            status = TaskStatus.cancelled;

        if (TaskStatus.error.equals(this.getActiveTaskStatus()))
            status = TaskStatus.error;

        logRunFinish(status, _recordCount);
    }


    private void update(TransformRun run)
    {
        TransformManager.get().updateTransformRun(getUser(), run);
    }


    private TransformRun getTransformRun()
    {
        TransformRun run  = TransformManager.get().getTransformRun(getContainer(), _runId);
        if (run == null)
        {
            getLogger().error("Unable to find database record for run with TransformRunId " + _runId);
            setStatus(TaskStatus.error);
        }

        return run;
    }


    public int getTransformRunId()
    {
        return _runId;
    }


    @Override
    public TaskPipeline getTaskPipeline()
    {
        if (null != _etlDescriptor)
            return _etlDescriptor.getTaskPipeline();

        return null;
    }

    public Set<String> getOutputFileBaseNames()
    {
        return _outputFileBaseNames;
    }

    //
    // TransformJobSupport
    //
    public TransformDescriptor getTransformDescriptor()
    {
        return _etlDescriptor;
    }

    @NotNull
    public TransformJobContext getTransformJobContext()
    {
        return _transformJobContext;
    }

    //
    // PropertiesJobSupport
    //
    public Map<PropertyDescriptor, Object> getProps()
    {
        Map<PropertyDescriptor, Object> propMap = new LinkedHashMap<>();
        Set<String> keys = _variableMap.keySet();
        for(String key : keys)
        {

            ParameterDescription pd = _variableMap.getDescriptor(key);
            if (null != pd)
            {
                if (pd instanceof SystemProperty)
                    propMap.put(((SystemProperty)pd).getPropertyDescriptor(), _variableMap.get(key));
                if (pd instanceof PropertyDescriptor)
                    propMap.put((PropertyDescriptor)pd, _variableMap.get(key));
            }
        }

        return propMap;
    }


    //
    // Called by the ExpGeneratorHelper after it has finished
    // generating the experiment run for the current set
    // of actions for this transform job.
    //
    public void clearActionSet(ExpRun run)
    {
        // Gather the rollup record count for all the tasks run
        Set<RecordedAction> actions = getActionSet().getActions();
        int recordCount = 0;

        for (RecordedAction action : actions)
        {
            recordCount += getRecordCountForAction(action);
        }

        _recordCount = recordCount;
        super.clearActionSet(run);
    }

    public int getRecordCountForAction(RecordedAction action)
    {
        int recordCount = 0;
        Map<PropertyDescriptor, Object> propMap = action.getProps();

        if (propMap.containsKey(TransformProperty.RecordsInserted.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsInserted.getPropertyDescriptor());

        if (propMap.containsKey(TransformProperty.RecordsDeleted.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsDeleted.getPropertyDescriptor());

        if (propMap.containsKey(TransformProperty.RecordsModified.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsModified.getPropertyDescriptor());

        return recordCount;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return ETL_PREFIX + (_etlDescriptor.getDescription() != null ? _etlDescriptor.getDescription() : _etlDescriptor.getName());
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    /*
     *   FileAnalysisJobSupport methods & parameters support
     *
    */
    private final Map<String, String> _parameters = new HashMap<>();

    private File _analysisDirectory;
    private String _baseName;

    @Override
    public Map<String, String> getParameters()
    {
        return _parameters;
    }

    @Override
    public String getProtocolName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getJoinedBaseName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getBaseNameForFileType(FileType fileType)
    {
        return getBaseName();
    }

    public void setBaseName(String baseName)
    {
        _baseName = baseName;
    }

    @Override
    public String getBaseName()
    {
        if (_baseName != null)
            return _baseName;
        else throw new IllegalStateException("File basename has not been set.");
    }

    @Override
    public File getDataDirectory()
    {
        throw new UnsupportedOperationException();
    }

    public void setAnalysisDirectory(File analysisDirectory)
    {
        _analysisDirectory = analysisDirectory;
    }

    @Override
    public File getAnalysisDirectory()
    {
        if (_analysisDirectory != null)
            return _analysisDirectory;
        else throw new IllegalStateException("File analysis directory has not been set.");
    }

    @Override
    public File findInputFile(String name)
    {
            return new File(getAnalysisDirectory(), name);
    }

    @Override
    public File findOutputFile(String name)
    {
        return new File(getAnalysisDirectory(), name);
    }

    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        File dir;
        if (outputDir.startsWith("/"))
        {
            dir = getPipeRoot().resolvePath(outputDir);
            if (dir == null)
                throw new RuntimeException("Output directory not under pipeline root: " + outputDir);

            if (!NetworkDrive.exists(dir))
            {
                getLogger().info("Creating output directory under pipeline root: " + dir);
                if (!dir.mkdirs())
                    throw new RuntimeException("Failed to create output directory under pipeline root: " + outputDir);
            }
        }
        else
        {
            dir = new File(getAnalysisDirectory(), outputDir);
            if (!NetworkDrive.exists(dir))
            {
                getLogger().info("Creating output directory under pipeline analysis dir: " + dir);
                if (!dir.mkdirs())
                    throw new RuntimeException("Failed to create output directory under analysis dir: " + outputDir);
            }
        }

        return new File(dir, fileName);
    }

    @Override
    public ParamParser createParamParser()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getParametersFile()
    {
        return null;
    }

    @Nullable
    @Override
    public File getJobInfoFile()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<File> getInputFiles()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        throw new UnsupportedOperationException();
    }
}
