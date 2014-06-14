/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.experiment.pipeline;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.FileXarSource;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.pipeline.ExpGeneratorId;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PropertiesJobSupport;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.FileType;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Creates an experiment run to represent the work that the task's job has done so far.
 * User: dax
 * Date: Apr 25, 2013
*/
public class ExpGeneratorTask extends PipelineJob.Task<ExpGeneratorTask.Factory>
{
    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ExpGeneratorId.class);
        }

        public Factory(Class namespaceClass)
        {
            super(namespaceClass);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new ExpGeneratorTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "GENERATING EXPERIMENT";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public ExpGeneratorTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }


    /**
     * The basic steps are:
     * 1. Start a transaction.
     * 2. Create a protocol and a run and insert them into the database, not loading the data files.
     * 4. Commit the transaction.
     */
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            // Keep track of all of the runs that have been created by this task
            ExpRun run = ExpGeneratorHelper.insertRun(getJob(), null, null);

            // save any job-level custom properties from the run
            if (getJob() instanceof PropertiesJobSupport)
            {
                PropertiesJobSupport jobSupport = getJob().getJobSupport(PropertiesJobSupport.class);
                for (Map.Entry<PropertyDescriptor, Object> prop : jobSupport.getProps().entrySet())
                {
                    run.setProperty(getJob().getUser(), prop.getKey(), prop.getValue());
                }
            }

            // Check if we've been cancelled. If so, delete any newly created runs from the database
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJob().getLogFile());
            if (statusFile != null && (PipelineJob.TaskStatus.cancelled.matches(statusFile.getStatus()) || PipelineJob.TaskStatus.cancelling.matches(statusFile.getStatus())))
            {
                getJob().info("Deleting run " + run.getName() + " due to cancellation request");
                run.delete(getJob().getUser());
            }
        }
        catch (RuntimeSQLException | ValidationException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        return new RecordedActionSet();
    }
}
