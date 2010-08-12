/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.pipeline.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * User: jeckels
 * Date: Feb 14, 2007
 */
public class MoveRunsPipelineJob extends PipelineJob
{
    private final int[] _runIds;
    private Container _sourceContainer;

    public MoveRunsPipelineJob(ViewBackgroundInfo info, Container sourceContainer, int[] runIds, PipeRoot root) throws IOException
    {
        super(ExperimentPipelineProvider.NAME, info, root);
        _runIds = runIds;
        _sourceContainer = sourceContainer;
        File moveRunLogsDir = ExperimentPipelineProvider.getMoveDirectory(root);
        moveRunLogsDir.mkdirs();
        File logFile = File.createTempFile("moveRun", ".log", moveRunLogsDir);
        setLogFile(logFile);

        StringBuilder sb = new StringBuilder();
        sb.append(getDescription());
        sb.append("\n");
        for (int runId : _runIds)
        {
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                sb.append("No run found for RunId ");
                sb.append(runId);
            }
            else
            {
                sb.append(run.getLSID());
                sb.append(", id = ");
                sb.append(runId);
            }
            sb.append("\n");
        }
        getLogger().info(sb.toString());
    }

    public String getDescription()
    {
        return "Move " + _runIds.length + " run" + (_runIds.length == 1 ? "" : "s") + " from " + _sourceContainer.getPath() + " to " + getContainer().getPath();
    }

    public ActionURL getStatusHref()
    {
        return null;
    }

    public int[] getRunIds()
    {
        return _runIds;
    }

    public Container getSourceContainer()
    {
        return _sourceContainer;
    }

    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(MoveRunsPipelineJob.class));
    }
}
