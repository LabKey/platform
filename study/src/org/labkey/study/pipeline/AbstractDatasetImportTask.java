/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.CohortManager;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


/**
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */
public abstract class AbstractDatasetImportTask<FactoryType extends AbstractDatasetImportTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
    private boolean hasErrors = false;

    transient private StudyManager _studyManager = StudyManager.getInstance();
    transient private CohortManager _cohortManager = CohortManager.getInstance();

    public AbstractDatasetImportTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Nullable public abstract File getDatasetsFile() throws Exception;
    public abstract StudyImpl getStudy();

    protected StudyManager getStudyManager()
    {
        return _studyManager;
    }

    protected CohortManager getCohortManager()
    {
        return _cohortManager;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        File datasetsFile;

        try
        {
            datasetsFile = getDatasetsFile();
        }
        catch (Exception e)
        {
            throw new PipelineJobException("Exception retrieving datasets file", e);
        }

        if (null != datasetsFile)
        {
            try
            {
                DatasetFileReader reader = new DatasetFileReader(datasetsFile, getStudy(), this);
                List<String> errors = new ArrayList<String>();

                try
                {
                    reader.validate(errors);

                    for (String error : errors)
                        logError(error);
                }
                catch (Exception x)
                {
                    logError("Parse failed: " + datasetsFile.getPath(), x);
                    return new RecordedActionSet();
                }

                PipelineJob pj = getJob();
                List<DatasetImportRunnable> runnables = reader.getRunnables();
                pj.info("Start batch " + (null == datasetsFile ? "" : datasetsFile.getName()));

                for (DatasetImportRunnable runnable : runnables)
                {
                    String validate = runnable.validate();
                    if (validate != null)
                    {
                        pj.setStatus(validate);
                        logError(validate);
                        continue;
                    }
                    String statusMsg = "" + runnable._action + " " + runnable._datasetDefinition.getLabel();
                    if (runnable._tsv != null)
                        statusMsg += " using file " + runnable._tsv.getName();
                    pj.setStatus(statusMsg);

                    try
                    {
                        runnable.run();
                    }
                    catch (Exception x)
                    {
                        logError("Unexpected error loading " + runnable._tsv.getName(), x);
                        assert hasErrors;
                    }
                }

                pj.info("Finish batch " + (null == datasetsFile ? "" : datasetsFile.getName()));

                pj.setStatus("UPDATE participants");
                pj.info("Updating particpant visits");
                getStudyManager().getVisitManager(getStudy()).updateParticipantVisits(pj.getUser());

                try
                {
                    pj.info("Updating particpant cohorts");
                    getCohortManager().updateParticipantCohorts(pj.getUser(), getStudy());
                }
                catch (SQLException e)
                {
                    // rethrow and catch below for central logging
                    throw new RuntimeException(e);
                }

                // materialize datasets only AFTER all other work has been completed; otherwise the background thread
                // materializing datasets will fight with other operations that may try to clear the materialized cache.
                // (updateParticipantVisits does this, for example)
                for (DatasetImportRunnable runnable : runnables)
                {
                    if (!(runnable instanceof ParticipantImportRunnable))
                        runnable.getDatasetDefinition().materializeInBackground(pj.getUser());
                }
                pj.info("Finished updating participants");
            }
            catch (RuntimeException x)
            {
                logError("Unexpected error", x);
                assert hasErrors;
                throw x;
            }
            finally
            {
                File lock = StudyPipeline.lockForDataset(getStudy(), datasetsFile);
                if (lock.exists() && lock.canRead() && lock.canWrite())
                    lock.delete();
            }
        }

        return new RecordedActionSet();
    }


    public enum Action
    {
        REPLACE,
        APPEND,
        DELETE,
//            MERGE
    }


    void logError(String s)
    {
        logError(s, null);
    }

    void logError(String s, Exception x)
    {
        hasErrors = true;
        getJob().error(s,x);
    }
}