/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataIteratorResultsImpl;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TSVColumnWriter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.steps.StepMeta;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    final protected TransformJobContext _context;
    final protected StepMeta _meta;
    final private TransformPipelineJob _txJob;
    final private RecordedActionSet _records = new RecordedActionSet();
    final private VariableMap _variableMap;

    // todo: make these long again but then update the AbstractParameter code
    // or else you'll get a cast exception. Note that when this change is made, StoredProcedureStep will need be updated
    // where it casts the output parameters.
    protected int _recordsInserted = -1;
    protected int _recordsDeleted = -1;
    protected int _recordsModified = -1;

    public static final String INPUT_ROLE = "Row Source";
    public static final String OUTPUT_ROLE = "Row Destination";
    protected boolean _validateSource = true;
    FilterStrategy _filterStrategy = null;
    private String _targetStringForURI;

    public TransformTask(TransformTaskFactory factory, PipelineJob job, StepMeta meta)
    {
        this(factory, job, meta, null);
    }

    public TransformTask(TransformTaskFactory factory, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        super(factory, job);

        _txJob = (TransformPipelineJob)job;
        if (null != _txJob)
            _variableMap = _txJob.getStepVariableMap(meta.getId());
        else
            _variableMap = new VariableMapImpl();
        _context = context;
        _meta = meta;
    }

    public static String getNumRowsString(int rows)
    {
        return rows + " row" + (rows != 1 ? "s" : "");
    }

    protected int appendToTarget(CopyConfig meta, Container c, User u, DataIteratorContext context, DataIteratorBuilder source, Logger log, @Nullable DbScope.Transaction txTarget)
    {
        if (CopyConfig.TargetTypes.query.equals(meta.getTargetType()))
            return appendToTargetQuery(meta, c, u, context, source, log, txTarget);
        else if (CopyConfig.TargetTypes.file.equals(meta.getTargetType()))
            return appendToTargetFile(meta, context, source, log);
        else throw new IllegalArgumentException("Invalid target type specified.");
    }

    private int appendToTargetQuery(CopyConfig meta, Container c, User u, DataIteratorContext context, DataIteratorBuilder source, Logger log, DbScope.Transaction txTarget)
    {
        QuerySchema querySchema =  DefaultSchema.get(u, c, meta.getTargetSchema());
        if (null == querySchema || null == querySchema.getDbSchema())
        {
            context.getErrors().addRowError(new ValidationException("Could not find schema: " + meta.getTargetSchema()));
            return -1;
        }
        TableInfo targetTableInfo = querySchema.getTable(meta.getTargetQuery());
        if (null == targetTableInfo)
        {
            context.getErrors().addRowError(new ValidationException("Could not find table: " +  meta.getFullTargetString()));
            return -1;
        }
        try
        {
            QueryUpdateService qus = targetTableInfo.getUpdateService();
            if (null == qus)
            {
                context.getErrors().addRowError(new ValidationException("Can't import into table: " + meta.getFullTargetString()));
                return -1;
            }
            qus.setBulkLoad(meta.isBulkLoad());

            Map<String, Object> extraContext = new HashMap<>();
            extraContext.put("dataSource", "etl");

            setTargetStringForURI(meta.getFullTargetString());

            Map<Enum, Object> options = new HashMap<>();
            options.put(QueryUpdateService.ConfigParameters.Logger, log);
            addTransactionOptions(targetTableInfo, c, context, txTarget, extraContext, options);

            log.info("Target option: " + meta.getTargetOptions());
            switch (meta.getTargetOptions())
            {
                case merge:
                    return qus.mergeRows(u, c, source, context.getErrors(), options, extraContext);
                case truncate:
                    int rows = qus.truncateRows(u, c, options, extraContext);
                    log.info("Deleted " + getNumRowsString(rows) + " from " + meta.getFullTargetString());
                    return qus.importRows(u, c, source, context.getErrors(), options, extraContext);
                case append:
                    return qus.importRows(u, c, source, context.getErrors(), options, extraContext);
                default:
                    throw new IllegalArgumentException("Invalid target option specified: " + meta.getTargetOptions());
            }
        }
        catch (BatchValidationException |QueryUpdateServiceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
        }
    }

    /**
     * Allows specifying transactions should be every n rows rather than a single wrapping transaction. Add "after insert" batch trigger as a pre-commit task.
     * Add "before insert" as post-commit task to init for the next transaction.
     */
    private void addTransactionOptions(final TableInfo target, final Container c, final DataIteratorContext context, DbScope.Transaction txTarget, final Map<String, Object> extraContext, final Map<Enum, Object> options)
    {
        if (_meta.getTransactionSize() > 0)
        {
            options.put(QueryUpdateService.ConfigParameters.TransactionSize, _meta.getTransactionSize());
            ((Logger)options.get(QueryUpdateService.ConfigParameters.Logger)).info("Target transactions will be committed every " + Integer.toString(_meta.getTransactionSize()) + " rows");
            if (target.hasTriggers(c))
            {
                txTarget.addCommitTask(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (BooleanUtils.isNotFalse((Boolean) extraContext.get("hasNextRow")))
                        {
                            try
                            {
                                target.fireBatchTrigger(c, TableInfo.TriggerType.INSERT, false, context.getErrors(), extraContext);
                                target.resetTriggers(c);
                            }
                            catch (BatchValidationException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }, DbScope.CommitTaskOption.PRECOMMIT);

                txTarget.addCommitTask(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (BooleanUtils.isNotFalse((Boolean) extraContext.get("hasNextRow")))
                        {
                            try
                            {
                                target.fireBatchTrigger(c, TableInfo.TriggerType.INSERT, true, context.getErrors(), extraContext);
                            }
                            catch (BatchValidationException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }, DbScope.CommitTaskOption.POSTCOMMIT);
            }
        }
    }

    private int appendToTargetFile(CopyConfig meta, DataIteratorContext context, DataIteratorBuilder source, Logger log)
    {

        // For now only doing truncate. Eventually 3 options: truncate, append, or create new unique name if filename already exists.
        // Append is a little tricky: need some overload methods on TextWriter to create a FileWriter with append == true,
        // and suppress writing column headers.
        // Also, who's to say the output columns match the existing file?
        try
        {
            File outputDir = _txJob.getPipeRoot().resolvePath(meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.dir));
            if (!outputDir.exists() && !outputDir.mkdirs())
            {
                context.getErrors().addRowError(new ValidationException("Can't create output directory: " + outputDir));
                return -1;
            }
            String baseName = makeFileBaseName(meta);
            String extension = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.extension);
            File outputFile = new File(outputDir, baseName + "." + extension);
            TSVGridWriter tsv = new TSVGridWriter(new DataIteratorResultsImpl(source.getDataIterator(context)));
            tsv.setColumnHeaderType(TSVColumnWriter.ColumnHeaderType.queryColumnName);

            String specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.columnDelimiter);
            if (specialChar != null)
                tsv.setDelimiterCharacter(specialChar.charAt(0));
            specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.rowDelimiter);
            if (specialChar != null)
                tsv.setRowSeparator(specialChar);
            specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.quote);
            if (specialChar != null)
                tsv.setQuoteCharacter(specialChar.charAt(0));

            tsv.write(outputFile);
            int rowCount = tsv.getDataRowCount();
            if (rowCount == 0)
                getJob().getParameters().put("etlOutputHadRows", "false");
            log.info("Wrote " + rowCount + " rows to file " + outputFile.toString());

            // Set some properties in the job for FileAnalysis pipeline steps
            // TODO: refactor these out as a separate method so other tasks that write files can do this too (none yet, but seems a file copy task is likely to be needed soon)
            _txJob.setAnalysisDirectory(outputDir);
            _txJob.setBaseName(baseName);
            setTargetStringForURI(outputFile.toURI().toString());

            return rowCount;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String makeFileBaseName(CopyConfig meta)
    {
        String name = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.baseName);

        // We support two special substitions in the base filename.
        // ${TransformRunId} will be substituted with the txJob transformRunId
        // ${Timestamp} will be substituted with a timestamp
        // One could imagine other future substitions, at which point it might be better to
        // use StringExpressionFactory. Also, could in future support SimpleDateFormat-style formatted timestamp substitutions.
        name = name.replaceAll("(?i)\\$\\{TransformRunId\\}", Integer.toString(_txJob.getTransformRunId()));
        name = name.replaceAll("(?i)\\$\\{Timestamp\\}", FileUtil.getTimestamp());

        return FileUtil.makeLegalName(name);
    }

    protected TransformPipelineJob getTransformJob()
    {
        return _txJob;
    }


    public VariableMap getVariableMap()
    {
        return _variableMap;
    }


    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedAction action = new RecordedAction(_factory.getId().getName());
        action.setStartTime(new Date());

        // Hack up a ViewContext so that we can run trigger scripts
        try (ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(getJob().getUser(), getJob().getContainer(), new ActionURL("dataintegration", "fake.view", getJob().getContainer())))
        {
            QueryService.get().setEnvironment(QueryService.Environment.USER, getJob().getUser());
            QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, getJob().getContainer());

            doWork(action);
            action.setEndTime(new Date());
            addProperties(action);
            action.setRecordCount(_txJob.getRecordCountForAction(action));
            _records.add(action);
            return _records;
        }
        finally
        {
            QueryService.get().clearEnvironment();
        }
    }

    private void addProperties(RecordedAction action)
    {
        for (String key : _variableMap.keySet())
        {
            // Only add entries from the variable map if they were added
            // with a property descriptor.  If the variable does not have
            // a descriptor then we do not want to persist it
            ParameterDescription pd = _variableMap.getDescriptor(key);
            if (pd != null)
            {
                Object value = _variableMap.get(key);
                if (pd instanceof SystemProperty)
                    action.addProperty(((SystemProperty)pd).getPropertyDescriptor(), value);
                if (pd instanceof PropertyDescriptor)
                    action.addProperty((PropertyDescriptor)pd, value);
            }
        }
    }

    public boolean hasWork()
    {
        QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _meta.getSourceSchema());
        if (null == sourceSchema || null == sourceSchema.getDbSchema())
            throw new IllegalArgumentException("Source schema not found: " + _meta.getSourceSchema());

        TableInfo t = sourceSchema.getTable(_meta.getSourceQuery());
        if (null == t)
            throw new IllegalArgumentException("Could not find table: " +  _meta.getSourceSchema() + "." + _meta.getSourceQuery());

        FilterStrategy filterStrategy = getFilterStrategy();
        return filterStrategy.hasWork();
    }

    protected FilterStrategy getFilterStrategy()
    {
        if (null == _filterStrategy)
        {
            FilterStrategy.Factory factory = null;
            ScheduledPipelineJobDescriptor jd = _context.getJobDescriptor();
            if (jd instanceof TransformDescriptor)
                factory = ((TransformDescriptor)jd).getDefaultFilterFactory();
            if (null == factory)
                factory = new ModifiedSinceFilterStrategy.Factory();

            _filterStrategy = factory.getFilterStrategy(_context, _meta);
        }

        return _filterStrategy;
    }

    abstract public void doWork(RecordedAction action) throws PipelineJobException;

    protected void recordWork(RecordedAction action)
    {
        if (-1 != _recordsInserted)
            action.addProperty(TransformProperty.RecordsInserted.getPropertyDescriptor(), _recordsInserted);
        if (-1 != _recordsDeleted)
            action.addProperty(TransformProperty.RecordsDeleted.getPropertyDescriptor(), _recordsDeleted);
        if (-1 != _recordsModified)
            action.addProperty(TransformProperty.RecordsModified.getPropertyDescriptor(), _recordsModified);
        try
        {
            // input is source table
            // output is dest table, or a stored procedure
            if (_meta.isUseSource())
            {
                action.addInput(new URI(_meta.getSourceSchema() + "." + _meta.getSourceQuery()), TransformTask.INPUT_ROLE);
            }
            if (_meta.isUseTarget())
            {
                action.addOutput(new URI(getTargetStringForURI()), TransformTask.OUTPUT_ROLE, false);
                // TODO: This isn't being set correctly for target files; however, attempts to fix this so far (like passing in a file instead of URI to
                // addOutput only make it worse; the file doesn't resolve correctly in the run for straight ETL's, and if there's a pipeline task,
                // it gets removed from the list of inputs so not found by ExperimentDataHandlers.

//                Object target = getTargetStringForURI();
//                if (target instanceof String)
//                    action.addOutput(new URI((String)target), TransformTask.OUTPUT_ROLE, false);
//                else if (target instanceof File)
//                    action.addOutput((File)target, TransformTask.OUTPUT_ROLE, false);
//                // if neither of those we don't know how to resolve it anyway
            }
        }
        catch (URISyntaxException ignore)
        {
        }
    }

    public boolean validate(CopyConfig meta, Container c, User u, Logger log)
    {
        DbScope sourceDbScope = null;
        if (_validateSource && meta.isUseSource()) // RemoteQuery has a source, but we don't validate it. Stored proc may not have a source.
        {
            QuerySchema sourceSchema = DefaultSchema.get(u, c, meta.getSourceSchema());
            if (null == sourceSchema || null == sourceSchema.getDbSchema())
            {
                log.error("Source schema not found: " + meta.getSourceSchema());
                return false;
            }
            else
                sourceDbScope = sourceSchema.getDbSchema().getScope();
        }

        if (meta.isUseTarget()) // Stored proc may not have a target
        {
            if (meta.getTargetType().equals(CopyConfig.TargetTypes.query))
            {
                QuerySchema targetSchema = DefaultSchema.get(u, c, meta.getTargetSchema());
                if (null == targetSchema || null == targetSchema.getDbSchema())
                {
                    log.error("Target schema not found: " + meta.getTargetSchema());
                    return false;
                }
                if (null == targetSchema.getTable(meta.getTargetQuery()))
                {
                    log.error("Target query not found: " + meta.getTargetQuery());
                    return false;
                }
                if (meta.getTransactionSize() > 0 && targetSchema.getDbSchema().getScope().equals(sourceDbScope) && sourceDbScope.getSqlDialect().isPostgreSQL())
                {
                    // See issue 22213. Postgres doesn't allow committing transactions mid-stream when source and target
                    // are on the same connection.
                    log.error("Specifying transaction size is not supported on Postgres when source and destination are in the same data source.");
                    return false;
                }
            }
            else // Target is file
            {
                File outputDir = _txJob.getPipeRoot().resolvePath(meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.dir));
                if (!outputDir.exists() && !outputDir.mkdirs())
                {
                    log.error("Target directory not accessible : " + outputDir.toString());
                    return false;
                }
            }
        }

        return true;
    }

    public String getTargetStringForURI()
    {
        return _targetStringForURI;
    }

    public void setTargetStringForURI(String targetStringForURI)
    {
        _targetStringForURI = targetStringForURI;
    }
}
