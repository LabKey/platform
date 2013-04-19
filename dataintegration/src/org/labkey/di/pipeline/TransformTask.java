/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTask.Factory>
{
    protected RecordedActionSet _records = new RecordedActionSet();

    public TransformTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        TransformJob job = (TransformJob) getJob();
        TransformJobSupport support = job.getJobSupport(TransformJobSupport.class);
        BaseQueryTransformDescriptor etl = support.getTransformDescriptor();
        TransformJobContext ctx = support.getTransformJobContext();

        // undone:  for multi-step transforms we'll need to have a better way to record
        // logging job start and stop
        job.logRunStart();

        //
        // do transform work here!
        //
        doWork();

        job.logRunFinish("Complete", 0);

        return _records;
    }

    abstract void doWork() throws PipelineJobException;


    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(TransformTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob pjob)
        {
            TransformJob job = (TransformJob)pjob;
            TransformJobSupport support = job.getJobSupport(TransformJobSupport.class);
            BaseQueryTransformDescriptor etl = support.getTransformDescriptor();
            TransformJobContext ctx = support.getTransformJobContext();

            // UNDONE: need to handle multiple steps
            return etl.createTask(job,ctx,0);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "ETL QueryTransformTask";
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }
    }
}
