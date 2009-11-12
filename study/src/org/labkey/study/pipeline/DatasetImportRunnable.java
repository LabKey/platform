/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.CPUTimer;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatasetImportRunnable implements Runnable
{
    private static final Logger LOG = PipelineJob.getJobLogger(DatasetImportRunnable.class);

    protected AbstractDatasetImportTask.Action _action = null;
    protected File _tsv;
    protected boolean _deleteAfterImport = false;
    protected String visitDatePropertyName = null;

    protected final DataSetDefinition _datasetDefinition;
    protected final AbstractDatasetImportTask _task;
    protected final Map<String, String> _columnMap = new DatasetFileReader.OneToOneStringMap();


    DatasetImportRunnable(AbstractDatasetImportTask task, DataSetDefinition ds, File tsv, AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Map<String, String> columnMap)
    {
        _task = task;
        _datasetDefinition = ds;
        _action = action;
        _deleteAfterImport = deleteAfterImport;
        _columnMap.putAll(columnMap);
        _tsv = tsv;
    }

    public String validate()
    {
        List<String> errors = new ArrayList<String>(5);
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

        if (_action == AbstractDatasetImportTask.Action.DELETE)
            return;

        if (null == _tsv)
            errors.add("No file specified");
        else if (!_tsv.exists())
            errors.add("File does not exist: " + _tsv.getName());
        else if (!_tsv.canRead())
            errors.add("Cannot read tsv: " + _tsv.getName());
    }


    public void run()
    {
        PipelineJob pj = _task.getJob();
        String name = getDatasetDefinition().getName();
        CPUTimer cpuReadFile = new CPUTimer(name + ": readFile");
        CPUTimer cpuDelete = new CPUTimer(name + ": delete");
        CPUTimer cpuImport = new CPUTimer(name + ": import");
        CPUTimer cpuCommit = new CPUTimer(name + ": commit");

        DbSchema schema  = StudyManager.getSchema();
        DbScope scope = schema.getScope();
        StudyImpl study = _task.getStudy();
        QCState defaultQCState = study.getDefaultPipelineQCState() != null ?
                StudyManager.getInstance().getQCStateForRowId(pj.getContainer(), study.getDefaultPipelineQCState().intValue()) : null;

        List<String> errors = new ArrayList<String>();
        validate(errors);

        if (!errors.isEmpty())
        {
            for (String e : errors)
                _task.logError(_tsv.getName() + " -- " + e);
            return;
        }

        try
        {
            scope.beginTransaction();

            if (_action == AbstractDatasetImportTask.Action.REPLACE || _action == AbstractDatasetImportTask.Action.DELETE)
            {
                assert cpuDelete.start();
                int rows = _task.getStudyManager().purgeDataset(study, _datasetDefinition);
                if (_action == AbstractDatasetImportTask.Action.DELETE)
                    pj.info(_datasetDefinition.getLabel() + ": Deleted " + rows + " rows");
                assert cpuDelete.stop();
            }

            if (_action == AbstractDatasetImportTask.Action.APPEND || _action == AbstractDatasetImportTask.Action.REPLACE)
            {
                assert cpuImport.start();

                String[] imported = _task.getStudyManager().importDatasetData(
                        study,
                        pj.getUser(),
                        _datasetDefinition,
                        new TabLoader(_tsv, true),
                        _tsv.lastModified(),
                        _columnMap,
                        errors,
                        false, //Set to TRUE if/when MERGE is implemented
                        defaultQCState);
                if (errors.size() == 0)
                {
                    assert cpuCommit.start();
                    scope.commitTransaction();
                    pj.info(_datasetDefinition.getLabel() + ": Successfully imported " + imported.length + " rows from " + _tsv);
                    assert cpuCommit.stop();
                    getDatasetDefinition().unmaterialize();
                }

                for (String err : errors)
                    _task.logError(_tsv.getName() + " -- " + err);

                if (_deleteAfterImport)
                {
                    boolean success = _tsv.delete();
                    if (success)
                        pj.info("Deleted file " + _tsv.getPath());
                    else
                        _task.logError("Could not delete file " + _tsv.getPath());
                }
                assert cpuImport.stop();
            }
        }
        catch (Exception x)
        {
            // If we have an active transaction, we need to roll it back
            // before we log the error or the logging will take place inside the transaction
            if (scope.isTransactionActive())
                scope.rollbackTransaction();

            _task.logError("Exception while importing file: " + _tsv, x);
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
            boolean debug = false;
            assert debug = true;
            if (debug)
            {
                LOG.debug(cpuReadFile);
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

    public File getFile()
    {
        return _tsv;
    }

    public String getFileName()
    {
        return null == _tsv ? null : _tsv.getName();
    }

    public DataSetDefinition getDatasetDefinition()
    {
        return _datasetDefinition;
    }

    public String getVisitDatePropertyName()
    {
        if (visitDatePropertyName == null && getDatasetDefinition() != null)
            return getDatasetDefinition().getVisitDatePropertyName();
        return visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        this.visitDatePropertyName = visitDatePropertyName;
    }
}
