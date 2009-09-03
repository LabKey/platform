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

import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.QCState;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class DatasetImportJob implements Runnable
{
    private static final Logger LOG = PipelineJob.getJobLogger(DatasetImportJob.class);

    protected AbstractDatasetImportTask.Action action = null;
    protected DataSetDefinition datasetDefinition;
    protected File tsv;
    protected boolean deleteAfterImport = false;
    protected Map<String, String> columnMap = new DatasetFileReader.OneToOneStringMap();
    protected String visitDatePropertyName = null;
    protected AbstractDatasetImportTask _task;


    DatasetImportJob(AbstractDatasetImportTask task, DataSetDefinition ds, File tsv, AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Map<String, String> columnMap)
    {
        _task = task;
        datasetDefinition = ds;
        this.action = action;
        this.deleteAfterImport = deleteAfterImport;
        this.columnMap.putAll(columnMap);
        this.tsv = tsv;
    }

    public String validate()
    {
        List<String> errors = new ArrayList<String>(5);
        validate(errors);
        return errors.isEmpty() ? null : errors.get(0);
    }

    public void validate(List<String> errors)
    {
        if (action == null)
            errors.add("No action specified");

        if (datasetDefinition == null)
            errors.add("Dataset not defined");
        else if (datasetDefinition.getTypeURI() == null)
            errors.add("Dataset " + (null != datasetDefinition.getName() ? datasetDefinition.getName() + ": " : "") + "type is not defined");

        if (action == AbstractDatasetImportTask.Action.DELETE)
            return;

        if (null == tsv)
            errors.add("No file specified");
        else if (!tsv.exists())
            errors.add("File does not exist: " + tsv.getName());
        else if (!tsv.canRead())
            errors.add("Cannot read tsv: " + tsv.getName());
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
                _task.logError(tsv.getName() + " -- " + e);
            return;
        }

        try
        {
            StringBuilder text = null;

            if (action == AbstractDatasetImportTask.Action.APPEND || action == AbstractDatasetImportTask.Action.REPLACE)
            {
                assert cpuReadFile.start();
                // UNDONE: there's a helper for this somewhere
                text = new StringBuilder((int)tsv.length());
                FileReader fr = new FileReader(tsv);
                BufferedReader reader = new BufferedReader(fr);
                String s;
                try
                {
                    while (null != (s = reader.readLine()))
                        text.append(s).append("\n");
                }
                finally
                {
                    reader.close();
                    fr.close();
                }
                assert cpuReadFile.stop();
            }

            scope.beginTransaction();

            if (action == AbstractDatasetImportTask.Action.REPLACE || action == AbstractDatasetImportTask.Action.DELETE)
            {
                assert cpuDelete.start();
                int rows = _task.getStudyManager().purgeDataset(study, datasetDefinition);
                if (action == AbstractDatasetImportTask.Action.DELETE)
                    pj.info(datasetDefinition.getLabel() + ": Deleted " + rows + " rows");
                assert cpuDelete.stop();
            }

            if (action == AbstractDatasetImportTask.Action.APPEND || action == AbstractDatasetImportTask.Action.REPLACE)
            {
                assert text != null;
                assert cpuImport.start();

                String[] imported = _task.getStudyManager().importDatasetTSV(
                        study,
                        pj.getUser(),
                        datasetDefinition,
                        text.toString(),
                        tsv.lastModified(),
                        columnMap,
                        errors,
                        false, //Set to TRUE if/when MERGE is implemented
                        defaultQCState);
                if (errors.size() == 0)
                {
                    assert cpuCommit.start();
                    scope.commitTransaction();
                    pj.info(datasetDefinition.getLabel() + ": Successfully imported " + imported.length + " rows from " + tsv);
                    assert cpuCommit.stop();
                    getDatasetDefinition().unmaterialize();
                }

                for (String err : errors)
                    _task.logError(tsv.getName() + " -- " + err);

                if (deleteAfterImport)
                {
                    boolean success = tsv.delete();
                    if (success)
                        pj.info("Deleted file " + tsv.getPath());
                    else
                        _task.logError("Could not delete file " + tsv.getPath());
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

            _task.logError("Exception while importing file: " + tsv, x);
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
        return action;
    }

    public File getFile()
    {
        return tsv;
    }

    public String getFileName()
    {
        return null == tsv ? null : tsv.getName();
    }

    public DataSetDefinition getDatasetDefinition()
    {
        return datasetDefinition;
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
