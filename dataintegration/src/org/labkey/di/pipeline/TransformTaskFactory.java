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

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.util.FileType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: daxh
 * Date: 4/17/13
 */
public class TransformTaskFactory extends AbstractTaskFactory<AbstractTaskFactorySettings, TransformTaskFactory>
{
    public TransformTaskFactory()
    {
        super(TransformTaskFactory.class);
    }

    public TransformTaskFactory(Class namespaceClass)
    {
        super(namespaceClass);
    }

    public TransformTaskFactory(Class namespaceClass, String id)
    {
        super(namespaceClass, id);
    }

    public TransformTaskFactory(TaskId id)
    {
        super(id);
    }

    public PipelineJob.Task createTask(PipelineJob pjob)
    {
        TransformPipelineJob job = (TransformPipelineJob)pjob;
        TransformJobSupport support = job.getJobSupport(TransformJobSupport.class);
        TransformDescriptor etl = support.getTransformDescriptor();
        TransformJobContext ctx = support.getTransformJobContext();
        return etl.createTask(this, job, ctx, 0);
    }

    public List<FileType> getInputTypes()
    {
        return Collections.emptyList();
    }

    public String getStatusName()
    {
        return "ETL " + getId().getName();
    }

    public boolean isJobComplete(PipelineJob job)
    {
        return false;
    }

    public List<String> getProtocolActionNames()
    {
        return Arrays.asList(getId().getName());
    }
}
