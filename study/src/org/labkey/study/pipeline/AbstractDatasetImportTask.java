/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
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

    public AbstractDatasetImportTask(FactoryType factory, PipelineJob job)
    {
        super(factory, job);
    }

    @Nullable public abstract File getDefinitionFile() throws Exception;
    public abstract StudyImpl getStudy();

    protected StudyManager getStudyManager()
    {
        return _studyManager;
    }

    public void prepareImport(List<String> errors) throws IOException, SQLException
    {
        File definitionFile = null;

        try
        {
            definitionFile = getDefinitionFile();
        }
        catch (Exception e)
        {
            e.printStackTrace();  // TODO: Do something better here
        }

        DatasetFileReader reader = new DatasetFileReader(definitionFile, getStudy(), this);
        reader.prepareImport(errors);
    }

    public RecordedActionSet run()
    {
        PipelineJob pj = getJob();
        File definitionFile = null;

        try
        {
            definitionFile = getDefinitionFile();
        }
        catch (Exception e)
        {
            e.printStackTrace();  // TODO: Do something better here
        }

        // TODO: if (null != definitionFile ...)
        try
        {
            DatasetFileReader reader = new DatasetFileReader(definitionFile, getStudy(), this);
            List<String> errors = new ArrayList<String>();

            try
            {
                reader.prepareImport(errors);

                for (String error : errors)
                    logError(error);
            }
            catch (Exception x)
            {
                logError("Parse failed: " + definitionFile.getPath(), x);
                return new RecordedActionSet();
            }

            List<DatasetImportJob> jobs = reader.getJobs();
            pj.info("Start batch " + (null == definitionFile ? "" : definitionFile.getName()));

            for (DatasetImportJob job : jobs)
            {
                String validate = job.validate();
                if (validate != null)
                {
                    pj.setStatus(validate);
                    logError(validate);
                    continue;
                }
                String statusMsg = "" + job.action + " " + job.datasetDefinition.getLabel();
                if (job.tsv != null)
                    statusMsg += " using file " + job.tsv.getName();
                pj.setStatus(statusMsg);

                try
                {
                    job.run();
                }
                catch (Exception x)
                {
                    logError("Unexpected error loading " + job.tsv.getName(), x);
                    assert hasErrors;
                }
            }

            pj.info("Finish batch " + (null == definitionFile ? "" : definitionFile.getName()));

            getStudyManager().getVisitManager(getStudy()).updateParticipantVisits(pj.getUser());

            try
            {
                getStudyManager().updateParticipantCohorts(pj.getUser(), getStudy());
            }
            catch (SQLException e)
            {
                // rethrow and catch below for central logging
                throw new RuntimeException(e);
            }

            // materialize datasets only AFTER all other work has been completed; otherwise the background thread
            // materializing datasets will fight with other operations that may try to clear the materialized cache.
            // (updateParticipantVisits does this, for example)
            for (DatasetImportJob job : jobs)
            {
                if (!(job instanceof ParticipantImportJob))
                    job.getDatasetDefinition().materializeInBackground(pj.getUser());
            }
        }
        catch (RuntimeException x)
        {
            logError("Unexpected error", x);
            assert hasErrors;
            throw x;
        }
        finally
        {
            File lock = StudyPipeline.lockForDataset(getStudy(), definitionFile);
            if (lock.exists() && lock.canRead() && lock.canWrite())
                lock.delete();
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