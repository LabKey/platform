/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.gwt.client.pipeline.GWTPipelineConfig;
import org.labkey.api.gwt.client.pipeline.GWTPipelineTask;
import org.labkey.api.gwt.client.pipeline.PipelineGWTService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.api.PipelineJobServiceImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class PipelineGWTServiceImpl extends BaseRemoteService implements PipelineGWTService
{
    public PipelineGWTServiceImpl(ViewContext viewContext)
    {
        super(viewContext);
    }

    @Override
    public GWTPipelineConfig getLocationOptions(String pipelineId)
    {
        try
        {
            TaskPipeline taskPipeline = PipelineJobServiceImpl.get().getTaskPipeline(new TaskId(pipelineId));
            if (taskPipeline == null)
            {
                throw new NotFoundException("Can't find pipelineId: " + pipelineId);
            }

            List<GWTPipelineTask> tasks = new ArrayList<>();
            for (TaskId taskId : taskPipeline.getTaskProgression())
            {
                TaskFactory taskFactory = PipelineJobServiceImpl.get().getTaskFactory(taskId);
                tasks.add(new GWTPipelineTask(taskId.toString(), taskFactory.getStatusName(), taskFactory.getGroupParameterName()));
            }

            return new GWTPipelineConfig(tasks);
        }
        catch (ClassNotFoundException e)
        {
            throw new NotFoundException("Can't find pipelineId: " + pipelineId, e);
        }
    }
}
