/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.pipeline.mule.test;

import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.util.FileType;

import java.util.Collections;
import java.util.List;

/**
 * Created by: jeckels
 * Date: 12/14/15
 */
public class DummyTaskFactory extends AbstractTaskFactory
{
    public DummyTaskFactory()
    {
        super(DummyTaskFactory.class);
        setLocation(DummyRemoteExecutionEngine.DummyConfig.LOCATION);
    }

    @Override
    public PipelineJob.Task createTask(PipelineJob job)
    {
        return new DummyTask(job);
    }

    @Override
    public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        return this;
    }

    @Override
    public List<FileType> getInputTypes()
    {
        return Collections.emptyList();
    }

    @Override
    public List<String> getProtocolActionNames()
    {
        return Collections.emptyList();
    }

    @Override
    public String getStatusName()
    {
        return "DUMMY";
    }

    @Override
    public boolean isJobComplete(PipelineJob job)
    {
        return false;
    }
}
