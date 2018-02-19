/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;


import java.io.IOException;

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

        String baseLogFileName = FileUtil.makeFileNameWithTimestamp("moveRun", ".log");
        LocalDirectory localDirectory = LocalDirectory.create(root, ExperimentService.MODULE_NAME, baseLogFileName,
                root.isCloudRoot() ? FileUtil.getTempDirectory().getPath() : FileUtil.getAbsolutePath(_sourceContainer, ExperimentPipelineProvider.getMoveDirectory(root)));

        setLocalDirectory(localDirectory);
        setLogFile(localDirectory.determineLogFile());

        getLogger().info(getDescription());
        for (int runId : _runIds)
        {
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                getLogger().info("No run found for RowId " + runId);
            }
            else
            {
                getLogger().info(run.getName() + " (RowId = " + runId + ")");
            }
        }
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
