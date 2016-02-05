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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.pipeline.api.TaskPipelineImpl;

import java.io.File;
import java.io.IOException;

/**
 * Created by: jeckels
 * Date: 12/14/15
 */
public class DummyPipelineJob extends PipelineJob
{
    public DummyPipelineJob(Container c, User user)
    {
        super(null, new ViewBackgroundInfo(c, user, null), PipelineService.get().findPipelineRoot(c));
        try
        {
            setLogFile(File.createTempFile("DummyPipelineJob", ".tmp"));
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Nullable
    @Override
    public TaskPipeline getTaskPipeline()
    {
        TaskPipelineImpl result = new TaskPipelineImpl(new TaskId(DummyRemoteExecutionEngine.class, "DummyPipeline"));
        result.setTaskProgression(new TaskId(DummyTaskFactory.class));
        return result;
    }
}
