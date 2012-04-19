/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.CohortManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */
public abstract class AbstractDatasetImportTask<FactoryType extends AbstractDatasetImportTaskFactory<FactoryType>> extends PipelineJob.Task<TaskFactory>
{
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

        doImport(datasetsFile, getJob(), getStudy());

        return new RecordedActionSet();
    }

    public static void doImport(File datasetsFile, PipelineJob job, StudyImpl study) throws PipelineJobException
    {
        if (null != datasetsFile)
        {
            try
            {
                QuerySnapshotService.get(StudyManager.getSchemaName()).pauseUpdates(study.getContainer());
                DatasetFileReader reader = new DatasetFileReader(datasetsFile, study, job);
                List<String> errors = new ArrayList<String>();

                try
                {
                    reader.validate(errors);

                    for (String error : errors)
                        job.error(error);
                }
                catch (Exception x)
                {
                    job.error("Parse failed: " + datasetsFile.getPath(), x);
                    return;
                }

                List<DatasetImportRunnable> runnables = reader.getRunnables();
                job.info("Start batch " + (null == datasetsFile ? "" : datasetsFile.getName()));

                List<DataSetDefinition> datasets = new ArrayList<DataSetDefinition>();

                for (DatasetImportRunnable runnable : runnables)
                {
                    String validate = runnable.validate();
                    if (validate != null)
                    {
                        job.setStatus(validate);
                        job.error(validate);
                        continue;
                    }
                    String statusMsg = "" + runnable._action + " " + runnable._datasetDefinition.getLabel();
                    datasets.add(runnable._datasetDefinition);
                    if (runnable._tsv != null)
                        statusMsg += " using file " + runnable._tsv.getName();
                    job.setStatus(statusMsg);

                    try
                    {
                        runnable.run();
                    }
                    catch (Exception x)
                    {
                        job.error("Unexpected error loading " + runnable._tsv.getName(), x);
                    }
                }

                job.info("Finish batch " + (null == datasetsFile ? "" : datasetsFile.getName()));

                job.setStatus("UPDATE participants");
                job.info("Updating participant visits");
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(job.getUser(), datasets);

                job.info("Finished updating participants");
            }
            catch (RuntimeException x)
            {
                job.error("Unexpected error", x);
                throw x;
            }
            finally
            {
                QuerySnapshotService.get(StudyManager.getSchemaName()).resumeUpdates(job.getUser(), study.getContainer());
                File lock = StudyPipeline.lockForDataset(study, datasetsFile);
                if (lock.exists() && lock.canRead() && lock.canWrite())
                    lock.delete();
            }
        }
    }


    public enum Action
    {
        REPLACE,
        APPEND,
        DELETE,
//            MERGE
    }
}