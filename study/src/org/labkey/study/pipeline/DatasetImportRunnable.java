/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.tika.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.Filter;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DatasetUpdateService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatasetImportRunnable implements Runnable
{
    private static final Logger LOG = PipelineJob.getJobLogger(DatasetImportRunnable.class);

    protected AbstractDatasetImportTask.Action _action;
    protected boolean _deleteAfterImport;
    protected Date _replaceCutoff;
    protected String visitDatePropertyURI = null;
    protected String visitDatePropertyName = null;

    protected final DatasetDefinition _datasetDefinition;
    protected final Logger _logger;
    protected final StudyImpl _study;
    @Nullable protected final StudyImportContext _studyImportContext;
    protected final Map<String, String> _columnMap = new DatasetFileReader.OneToOneStringMap();

    protected VirtualFile _root;
    protected String _fileName;

    DatasetImportRunnable(Logger logger, StudyImpl study, DatasetDefinition ds, VirtualFile root, String fileName,
                          AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Date defaultReplaceCutoff,
                          Map<String, String> columnMap, @Nullable StudyImportContext studyImportContext)
    {
        _logger = logger;
        _study = study;
        _datasetDefinition = ds;
        _action = action;
        _deleteAfterImport = deleteAfterImport;
        _replaceCutoff = defaultReplaceCutoff;
        _columnMap.putAll(columnMap);

        _root = root;
        _fileName = fileName;
        _studyImportContext = studyImportContext;
    }

    public String validate()
    {
        List<String> errors = new ArrayList<>(5);
        validate(errors);
        return errors.isEmpty() ? null : errors.get(0);
    }

    public void validate(List<String> errors)
    {
        if (_action == null)
            errors.add("No action specified");

        if (_datasetDefinition == null)
            errors.add("Dataset not defined");
        else if (_datasetDefinition.getTypeURI() == null)
            errors.add("Dataset " + (null != _datasetDefinition.getName() ? _datasetDefinition.getName() + ": " : "") + "type is not defined");
        else if (null == _datasetDefinition.getStorageTableInfo())
            errors.add("No database table found for dataset " + _datasetDefinition.getName());

        if (_action == AbstractDatasetImportTask.Action.DELETE)
            return;

        if (null == _fileName)
            errors.add("No file specified");
        else if (!_root.list().contains(_fileName))
            errors.add("File does not exist: " + _fileName);
    }

    public ColumnDescriptor[] getColumns()
    {
        try (InputStream is = _root.getInputStream(_fileName))
        {
            DataLoader loader = DataLoaderService.get().createLoader(_fileName, null, is, true, _study.getContainer(), TabLoader.TSV_FILE_TYPE);
            return loader.getColumns();
        }
        catch (Exception x)
        {
            _logger.error("Exception while importing dataset " + _datasetDefinition.getName() + " from " + _fileName, x);
        }
        return new ColumnDescriptor[0];
    }

    @Override
    public void run()
    {
        String name = getDatasetDefinition().getName();
        CPUTimer cpuDelete = new CPUTimer(name + ": delete");
        CPUTimer cpuImport = new CPUTimer(name + ": import");
        CPUTimer cpuCommit = new CPUTimer(name + ": commit");

        DbSchema schema  = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        DataState defaultQCState = _study.getDefaultPipelineQCState() != null ?
                DataStateManager.getInstance().getStateForRowId(_study.getContainer(), _study.getDefaultPipelineQCState().intValue()) : null;

        List<String> validateErrors = new ArrayList<>();
        validate(validateErrors);

        if (!validateErrors.isEmpty())
        {
            for (String e : validateErrors)
                _logger.error(_fileName + " -- " + e);
            return;
        }

        // resources to handle in finally or catch
        BatchValidationException batchErrors = new BatchValidationException();
        InputStream is = null;
        DataLoader loader = null;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            final String visitDatePropertyURI = getVisitDateURI(_studyImportContext.getUser());
            boolean useCutoff =
                    _action == AbstractDatasetImportTask.Action.REPLACE &&
                            visitDatePropertyURI != null &&
                            _replaceCutoff != null;

            User user = _studyImportContext.getUser();
            Container c = _studyImportContext.getContainer();
            QuerySchema qs = DefaultSchema.get(user,c).getSchema("study");
            TableInfo datasetTable = null==qs ? null : qs.getTable(_datasetDefinition.getName());
            QueryUpdateService qus = null == datasetTable ? null : datasetTable.getUpdateService();
            if (null == qus)
                throw UnexpectedException.wrap(new NullPointerException(),"Table not found for table: " + _datasetDefinition.getName());

            Map<Enum,Object> config = new HashMap<>();
            config.put(DatasetUpdateService.Config.CheckForDuplicates, DatasetDefinition.CheckForDuplicates.sourceOnly);
            config.put(DatasetUpdateService.Config.StudyImportMaps, _studyImportContext.getTableIdMapMap());
            if (null != defaultQCState)
                config.put(DatasetUpdateService.Config.DefaultQCState, defaultQCState);
            config.put(QueryUpdateService.ConfigParameters.Logger, _logger);
            config.put(DatasetUpdateService.Config.SkipResyncStudy, Boolean.TRUE);

            final Integer[] skippedRowCount = new Integer[]{0};

            if (_action == AbstractDatasetImportTask.Action.APPEND || _action == AbstractDatasetImportTask.Action.REPLACE)
            {
                is = _root.getInputStream(_fileName);
                loader = DataLoaderService.get().createLoader(_fileName, null, is, true, _studyImportContext.getContainer(), TabLoader.TSV_FILE_TYPE);
                if (useCutoff)
                {
                    loader.setMapFilter(row -> {
                        Object o = row.get(visitDatePropertyURI);

                        // Allow rows with no Date or those that have failed conversion (e.g., value is a StudyManager.CONVERSION_ERROR)
                        if (!(o instanceof Date))
                            return true;

                        // Allow rows after the cutoff date.
                        if (((Date) o).compareTo(_replaceCutoff) > 0)
                            return true;

                        skippedRowCount[0]++;
                        return false;
                    });
                }
            }

            assert cpuImport.start();
            _logger.info(_datasetDefinition.getLabel() + ": Starting import from " + _fileName);

            boolean tryDataDiffing = null != DataIntegrationService.get() &&
                    _datasetDefinition.getKeyManagementType() == Dataset.KeyManagementType.None &&
                    _action != AbstractDatasetImportTask.Action.DELETE;
            int count = 0;
            ArrayList<String> importMessages = new ArrayList<>();

            if (tryDataDiffing)
            {
                var b = DataIntegrationService.get().createReimportBuilder(user, c, datasetTable, batchErrors);
                b.setSource(loader);
                if (_action== AbstractDatasetImportTask.Action.REPLACE)
                {
                    b.setReimportOptions(Set.of(DataIntegrationService.ReimportOperations.DELETE, DataIntegrationService.ReimportOperations.REPLACE, DataIntegrationService.ReimportOperations.INSERT));
                }
                else
                {
                    assert _action == AbstractDatasetImportTask.Action.APPEND;
                    b.setReimportOptions(Set.of(DataIntegrationService.ReimportOperations.INSERT));
                }
                b.setConfigParameters(config);
                b.validate();

                if (batchErrors.hasErrors())
                {
                    batchErrors.clear();
                    tryDataDiffing = false;
                }
                else
                {
                    b.execute();
                    if (!batchErrors.hasErrors())
                    {
                        if (0 < b.getProcessed())
                        {
                            String msg = (_datasetDefinition.getLabel() + ": Processed " + b.getProcessed() + " rows from " + _fileName);
                            if (useCutoff && skippedRowCount[0] > 0)
                                msg += " (skipped " + skippedRowCount[0] + " rows older than cutoff)";
                            importMessages.add(msg);
                        }
                        if (0 < b.getDeleted())
                        {
                            importMessages.add(_datasetDefinition.getLabel() + ": Deleted " + b.getDeleted() + " rows");
                        }
                        if (0 < b.getInserted())
                        {
                            count += b.getInserted();
                            importMessages.add(_datasetDefinition.getLabel() + ": Inserted " + b.getInserted() + " rows");
                        }
                        if (0 < b.getMerged())
                        {
                            count += b.getMerged();
                            importMessages.add(_datasetDefinition.getLabel() + ": Merged " + b.getMerged() + " rows");
                        }
                        if (0 < b.getUpdated())
                        {
                            count += b.getUpdated();
                            importMessages.add(_datasetDefinition.getLabel() + ": Updated " + b.getUpdated() + " rows");
                        }
                    }
                }
            }

            if (!tryDataDiffing)
            {
                if (_action == AbstractDatasetImportTask.Action.REPLACE || _action == AbstractDatasetImportTask.Action.DELETE)
                {
                    assert cpuDelete.start();
                    _logger.info(_datasetDefinition.getLabel() + ": Starting delete" + (useCutoff ? " of rows newer than " + _replaceCutoff : ""));
                    int rows = StudyManager.getInstance().purgeDataset(_datasetDefinition, useCutoff ? _replaceCutoff : null);
                    _logger.info(_datasetDefinition.getLabel() + ": Deleted " + rows + " rows");
                    assert cpuDelete.stop();
                }


                assert cpuImport.start();
                _logger.info(_datasetDefinition.getLabel() + ": Starting import from " + _fileName);

                count = qus.importRows(user, c, loader, batchErrors, config, null);

                String msg = _datasetDefinition.getLabel() + ": Successfully imported " + count + " rows from " + _fileName;
                if (useCutoff && skippedRowCount[0] > 0)
                    msg += " (skipped " + skippedRowCount[0] + " rows older than cutoff)";
                importMessages.add(msg);
            }

            if (!batchErrors.hasErrors())
            {
                // optional check if new visits exist before committing, visit based timepoint studies only
                boolean shouldCommit = true;
                if (_studyImportContext.isFailForUndefinedVisits() && _study.getTimepointType() == TimepointType.VISIT)
                {
                    List<Double> undefinedSequenceNums = StudyManager.getInstance().getUndefinedSequenceNumsForDataset(_datasetDefinition.getContainer(), _datasetDefinition.getDatasetId());
                    if (!undefinedSequenceNums.isEmpty())
                    {
                        Collections.sort(undefinedSequenceNums);
                        _logger.error("The following undefined visits exist in the dataset data: " + StringUtils.join(undefinedSequenceNums, ", "));
                        shouldCommit = false;
                    }
                }

                if (shouldCommit)
                {
                    assert cpuCommit.start();
                    transaction.commit();
                    for (var msg : importMessages)
                        _logger.info(msg);
                    assert cpuCommit.stop();
                }
            }
            assert cpuImport.stop();
        }
        catch (Exception x)
        {
            _logger.error("Exception while importing dataset " + _datasetDefinition.getName() + " from " + _fileName, x);
        }
        finally
        {
            IOUtils.closeQuietly(is);
            for (ValidationException err : batchErrors.getRowErrors())
                _logger.error(_fileName + " -- " + err.getMessage());

            if (_deleteAfterImport)
            {
                boolean success = _root.delete(_fileName);
                if (success)
                    _logger.info("Deleted file " + _fileName);
                else
                    _logger.error("Could not delete file " + _fileName);
            }

            if (loader != null)
                loader.close();

            boolean debug = false;
            assert debug = true;
            if (debug)
            {
                LOG.debug(cpuDelete);
                LOG.debug(cpuImport);
                LOG.debug(cpuCommit);
            }
        }
    }

    public AbstractDatasetImportTask.Action getAction()
    {
        return _action;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public DatasetDefinition getDatasetDefinition()
    {
        return _datasetDefinition;
    }

    public String getVisitDatePropertyName()
    {
        if (visitDatePropertyName == null && getDatasetDefinition() != null)
            return getDatasetDefinition().getVisitDateColumnName();
        return visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        this.visitDatePropertyName = visitDatePropertyName;
    }

    public String getVisitDateURI(User user)
    {
        if (visitDatePropertyURI == null)
        {
            TableInfo ti = _datasetDefinition.getTableInfo(user);
            if (null != ti)
            {
                for (ColumnInfo col : ti.getColumns())
                {
                    if (col.getName().equalsIgnoreCase(getVisitDatePropertyName()))
                        visitDatePropertyURI = col.getPropertyURI();
                }
            }
            if (visitDatePropertyURI == null)
                visitDatePropertyURI = DatasetDefinition.getVisitDateURI();
        }
        return visitDatePropertyURI;
    }
}
