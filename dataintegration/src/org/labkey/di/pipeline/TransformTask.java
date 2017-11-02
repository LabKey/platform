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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataIteratorResultsImpl;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.di.TransformDataIteratorBuilder;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    final protected TransformJobContext _context;
    final protected StepMeta _meta;
    protected final TransformPipelineJob _txJob;
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
    private Object _targetForURI;

    private final int DELETE_BATCH_SIZE = 1000;

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
        // Only persist constants to the transform state for steps that have them and write to a target query/file
        if (!_meta.getConstants().isEmpty())
        {
            Map<String, Object> persistConstants = _meta.getConstants().entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue));
            _variableMap.put(TransformProperty.Constants, persistConstants);
        }

        if (CopyConfig.TargetTypes.query.equals(meta.getTargetType()))
            return appendToTargetQuery(meta, c, u, context, source, log, txTarget);
        else if (CopyConfig.TargetTypes.file.equals(meta.getTargetType()))
            return appendToTargetFile(meta, context, source, log);
        else throw new ConfigurationException("Invalid target type specified.");
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

            setTargetForURI(meta.getFullTargetString());

            Map<Enum, Object> options = new HashMap<>();
            options.put(QueryUpdateService.ConfigParameters.Logger, log);
            addTransactionOptions(targetTableInfo, c, context, txTarget, extraContext, options);

            log.info("Target option: " + meta.getTargetOptions());
            int rowCount;
            switch (meta.getTargetOptions())
            {
                case truncate:
                    int rows = qus.truncateRows(u, c, options, extraContext);
                    log.info("Deleted " + getNumRowsString(rows) + " from " + meta.getFullTargetString());
                    return qus.importRows(u, c, source, context.getErrors(), options, extraContext);
                case merge:
                    rowCount = qus.mergeRows(u, c, source, context.getErrors(), options, extraContext);
                    break;
                case append:
                    rowCount = qus.importRows(u, c, source, context.getErrors(), options, extraContext);
                    break;
                default:
                    throw new ConfigurationException("Invalid target option specified: " + meta.getTargetOptions());
            }

            // We shouldn't proceed to deleting records if there were errors in the main step operation. In SQL Server, this could leave data in an unknown state.
            // On Postgres, we'll get an exception trying to reuse the connection.
            if (context.getErrors().hasErrors())
                return rowCount;

            _recordsDeleted = deleteRows(targetTableInfo, qus, context, c, u, options, extraContext, log);
            return rowCount;
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
    private int deleteRows(TableInfo targetTable, QueryUpdateService qus, DataIteratorContext dataIteratorContext, Container c, User u, Map<Enum, Object> options, Map<String, Object> extraContext, Logger log) throws SQLException, QueryUpdateServiceException, BatchValidationException
    {
        if (null == _filterStrategy || _filterStrategy.getDeletedRowsSource() == null)
            return -1;

        // If the specified target deletion key col is the sole pk column, we can use it directly.
        // Otherwise we'll use it as a lookup to get a pk value map
        boolean directDelete = targetTable.getPkColumnNames().contains(_filterStrategy.getTargetDeletionKeyCol()) && targetTable.getPkColumns().size() == 1;
        int total = 0;
        try (Results results = new TableSelector(_filterStrategy.getDeletedRowsTinfo(), Collections.singleton(_filterStrategy.getDeletedRowsKeyCol()), _filterStrategy.getFilter(getVariableMap(), true), null).getResults(false))
        {
            if (directDelete)
            {
                List<Map<String, Object>> deleteKeys = new ArrayList<>();
                while (results.next())
                {
                    Map<String, Object> key = new HashMap<>();
                    key.put(_filterStrategy.getTargetDeletionKeyCol(), results.getObject(_filterStrategy.getDeletedRowsKeyCol()));
                    deleteKeys.add(key);
                    if (deleteKeys.size() == DELETE_BATCH_SIZE)
                    {
                        total += processDirectDeleteBatch(qus, dataIteratorContext, c, u, options, extraContext, deleteKeys);
                        deleteKeys.clear();
                    }
                }
                if (!deleteKeys.isEmpty())
                    total += processDirectDeleteBatch(qus, dataIteratorContext, c, u, options, extraContext, deleteKeys);
            }
            else
            {
                List<Object> deleteVals = new ArrayList<>();
                while (results.next())
                {
                    deleteVals.add(results.getObject(_filterStrategy.getDeletedRowsKeyCol()));
                    if (deleteVals.size() == DELETE_BATCH_SIZE)
                    {
                        total += processIndirectDeleteBatch(targetTable, qus, dataIteratorContext, c, u, options, extraContext, deleteVals);
                        deleteVals.clear();
                    }
                }
                if (!deleteVals.isEmpty())
                    total += processIndirectDeleteBatch(targetTable, qus, dataIteratorContext, c, u, options, extraContext, deleteVals);
            }
        }
        return total;
    }

    private int processIndirectDeleteBatch(TableInfo targetTable, QueryUpdateService qus, DataIteratorContext dataIteratorContext, Container c, User u, Map<Enum, Object> options, Map<String, Object> extraContext, List<Object> deleteVals) throws SQLException, QueryUpdateServiceException, BatchValidationException
    {
        List<Map<String, Object>> deleteKeys = new ArrayList<>();
        int batchTotal = 0;
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(_filterStrategy.getTargetDeletionKeyCol()), deleteVals, CompareType.IN);
        try (Results targetDeletes = new TableSelector(targetTable, targetTable.getPkColumns(), filter, null).getResults(false))
        {
            while (targetDeletes.next())
            {
                Map<String, Object> key = new HashMap<>();
                for (Map.Entry<FieldKey, Object> entry : targetDeletes.getFieldKeyRowMap().entrySet())
                {
                    key.put(entry.getKey().toString(), entry.getValue());
                }
                deleteKeys.add(key);
                if (deleteKeys.size() == DELETE_BATCH_SIZE)
                {
                    batchTotal += processDirectDeleteBatch(qus, dataIteratorContext, c, u, options, extraContext, deleteKeys);
                    deleteKeys.clear();
                }
            }
            if (!deleteKeys.isEmpty())
                batchTotal += processDirectDeleteBatch(qus, dataIteratorContext, c, u, options, extraContext, deleteKeys);
        }
        return batchTotal;
    }

    private int processDirectDeleteBatch(QueryUpdateService qus, DataIteratorContext dataIteratorContext, Container c, User u, Map<Enum, Object> options, Map<String, Object> extraContext, List<Map<String, Object>> deleteKeys) throws BatchValidationException, QueryUpdateServiceException, SQLException
    {
        try
        {
            return qus.deleteRows(u, c, deleteKeys, options, extraContext).size();
        }
        catch (InvalidKeyException e) // TODO: Do we care?
        {
            dataIteratorContext.getErrors().addRowError(new ValidationException("InvalidKey: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Allows specifying transactions should be every n rows rather than a single wrapping transaction. Add "after insert" batch trigger as a pre-commit task.
     * Add "before insert" as post-commit task to init for the next transaction.
     */
    private void addTransactionOptions(final TableInfo target, final Container c, final DataIteratorContext context, DbScope.Transaction txTarget, final Map<String, Object> extraContext, final Map<Enum, Object> options)
    {
        if (_meta.getBatchSize() > 0)
        {
            options.put(QueryUpdateService.ConfigParameters.TransactionSize, _meta.getBatchSize());
            ((Logger)options.get(QueryUpdateService.ConfigParameters.Logger)).info("Target transactions will be committed every " + Integer.toString(_meta.getBatchSize()) + " rows");
            if (target.hasTriggers(c))
            {
                txTarget.addCommitTask(() -> {
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
                }, DbScope.CommitTaskOption.PRECOMMIT);

                txTarget.addCommitTask(() -> {
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
        // Update 3/3/16 ... and now that we're adding feature to write to multiple files based on batch size, I'm not sure what an
        // append would look like at all.
        try
        {
            Results results = new DataIteratorResultsImpl(source.getDataIterator(context));
            FieldKey batchColumn = meta.getBatchColumn() == null ? null : FieldKey.fromParts(meta.getBatchColumn());
            if (meta.getBatchSize() > 0 && null != batchColumn && !results.hasColumn(batchColumn))
            {
                StringBuilder sb = new StringBuilder("Batch column '").append(batchColumn).append("' not found in etl source results.");
                if (meta.getColumnTransforms().containsKey(batchColumn.toString()))
                {
                    sb.append("\nThis source column name is mapped in the etl xml via destination columnTransforms. Specify the target column name '")
                            .append(meta.getColumnTransforms().get(batchColumn.toString()))
                            .append("' as the batch column instead.");
                }
                throw new ConfigurationException(sb.toString());
            }


            File outputDir = _txJob.getPipeRoot().resolvePath(meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.dir));
            if (null == outputDir || (!outputDir.exists() && !outputDir.mkdirs()))
            {
                context.getErrors().addRowError(new ValidationException("Can't create output directory: " + outputDir));
                return -1;
            }

            TSVGridWriter tsv = new TSVGridWriter(results);
            tsv.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey); // CONSIDER: Use FieldKey instead
            String specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.columnDelimiter);
            if (specialChar != null)
                tsv.setDelimiterCharacter(specialChar.charAt(0));
            specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.rowDelimiter);
            if (specialChar != null)
                tsv.setRowSeparator(specialChar);
            specialChar = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.quote);
            if (specialChar != null)
                tsv.setQuoteCharacter(specialChar.charAt(0));

            String baseName = makeFileBaseName(meta);
            String extension = meta.getTargetFileProperties().get(CopyConfig.TargetFileProperties.extension);
            List<File> outputFiles = tsv.writeBatchFiles(outputDir, baseName, extension, meta.getBatchSize(), batchColumn);
            int rowCount = tsv.getDataRowCount();
            if (rowCount == 0)
                getJob().getParameters().put("etlOutputHadRows", "false");
            log.info("Wrote " + rowCount + " total rows to file" + (outputFiles.size() == 1 ? "" : "s") +":\n");
            outputFiles.forEach((file) ->
                {
                    _txJob.getOutputFileBaseNames().add(StringUtils.substringBefore(file.getName(), extension));
                    log.info(file.toString() + "\n");
                });

            // Set some properties in the job for FileAnalysis pipeline steps
            _txJob.setAnalysisDirectory(outputDir);
            _txJob.setBaseName(baseName);
            setTargetForURI(outputFiles);

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
        // One could imagine other future substitutions, at which point it might be better to
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


    @NotNull
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
            if (_meta.isSaveState())
                getTransformJob().saveVariableMap(false);
            return _records;
        }
        catch (Exception x) // It should only ever be either a PipelineJobException or a RuntimeException, but to be safe...
        {
            _txJob.closeWrappingTransactions(true,true);
            throw x;
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

    protected boolean sourceHasWork()
    {
        QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _meta.getSourceSchema());
        if (null == sourceSchema || null == sourceSchema.getDbSchema())
            throw new ConfigurationException("Source schema not found: " + _meta.getSourceSchema());

        TableInfo t = sourceSchema.getTable(_meta.getSourceQuery());
        if (null == t)
            throw new ConfigurationException("Could not find table: " +  _meta.getSourceSchema() + "." + _meta.getSourceQuery());

        return getFilterStrategy().hasWork();
    }

    public boolean hasWork()
    {
        // For many step types we can't tell if there's work to be done, so we were hardcoded to return true.
        // Now if a stored proc is gating the etl, they'll report false and let the stored proc decide.
        // Obviously this shouldn't be used in combination with steps that should always run.
        return !isEtlGatedByStep();
    }

    protected boolean isEtlGatedByStep()
    {
        return ((TransformDescriptor)_context.getJobDescriptor()).isGatedByStep();
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
                factory = new ModifiedSinceFilterStrategy.Factory(null);

            _filterStrategy = factory.getFilterStrategy(_context, _meta);
        }

        return _filterStrategy;
    }

    abstract public void doWork(RecordedAction action) throws PipelineJobException;

    @SuppressWarnings("unchecked")
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
                Object target = getTargetForURI();
                if (target instanceof String)
                    action.addOutput(new URI((String)target), TransformTask.OUTPUT_ROLE, false);
                else if (target instanceof List) // It should be a list of Files
                {
                    ((List) target).stream().filter(targetItem -> targetItem instanceof File).forEach(targetItem -> action.addOutput((File) targetItem, TransformTask.OUTPUT_ROLE, false));
                }
                else
                {
                    // if neither of those we don't know how to resolve it anyway
                    if (null == target)
                        _txJob.getLogger().warn("Output URI was null. This should never happen.");
                    else
                        _txJob.getLogger().warn("Unknown object type for output URI: " + target.getClass().getName() + ". This should never happen.");
                }
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
                TableInfo targetTable = targetSchema.getTable(meta.getTargetQuery());
                if (null == targetTable)
                {
                    log.error("Target query '" + meta.getTargetQuery() + "' not found in schema '" + meta.getTargetSchema() + "'");
                    return false;
                }
                if (null != getFilterStrategy().getDeletedRowsSource())
                {
                    if (null == getFilterStrategy().getDeletedRowsSource().getTargetKeyColumnName())
                    {
                        List<String> targetPkCols = targetTable.getPkColumnNames();
                        if (targetPkCols.size() != 1)
                        {
                            log.error("To delete from a target query, target must either have exactly one primary key column, or the match column should be specified in the xml.");
                            return false;
                        }
                        getFilterStrategy().setTargetDeletionKeyCol(targetPkCols.get(0));
                    }
                    else
                    {
                        ColumnInfo targetDeletionKeyCol = targetTable.getColumn(FieldKey.fromParts(getFilterStrategy().getDeletedRowsSource().getTargetKeyColumnName()));
                        if (null == targetDeletionKeyCol)
                        {
                            log.error("Match key for deleted rows not found in target query: " + getFilterStrategy().getDeletedRowsSource().getTargetKeyColumnName());
                            return false;
                        }
                        getFilterStrategy().setTargetDeletionKeyCol(targetDeletionKeyCol.getColumnName());
                    }
                }
                if (meta.getBatchSize() > 0 && targetSchema.getDbSchema().getScope().equals(sourceDbScope) && sourceDbScope.getSqlDialect().isPostgreSQL())
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

    public Object getTargetForURI()
    {
        return _targetForURI;
    }

    public void setTargetForURI(Object targetForURI)
    {
        _targetForURI = targetForURI;
    }

    @NotNull
    protected TransformDataIteratorBuilder createTransformDataIteratorBuilder(int transformRunId, DataIteratorBuilder source, Logger log)
    {
        return new TransformDataIteratorBuilder(transformRunId, source, log, getTransformJob(), _factory.getStatusName(), _meta.getColumnTransforms(), _meta.getConstants(), _meta.getAlternateKeys());
    }

    /**
     * Encapsulate the logic of whether or not to start a transaction, rather than have ternary operators in all the try-with-resources
     * blocks of different ETL step types.
     * If there is already an active transaction in the scope's connection, it was started by the option of wrapping an entire multi-step
     * ETL job in a transaction. In this case we can't reuse the existing transaction (we wouldn't want to commit it mid job),
     * nor do we allow nesting of transactions here.
     *
     * @param scope This may be null, most likely when the source and target schemas are in the same db scope
     * @param useTransaction Likely set from the etl xml, the majority of the time this will be true. False when the etl xml
     *                      has been configured to not use a transaction on the step. Kind of silly to call this method when this is false, but it makes the
     *                       try-with-resources blocks more readable/maintainable.
     * @return A new transaction, or null
     */
    @Nullable
    protected DbScope.Transaction conditionalGetTransaction(@Nullable DbScope scope, boolean useTransaction)
    {
        if (null == scope || !useTransaction || scope.isTransactionActive())
            return null;
        else return scope.ensureTransaction();
    }
}
