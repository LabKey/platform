/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

/**
 * User: jeckels
 * Date: Feb 14, 2007
 */
public class MoveRunsPipelineJob extends PipelineJob
{
    private final int[] _runIds;
    private Container _sourceContainer;

    @JsonCreator
    protected MoveRunsPipelineJob(@JsonProperty("_runIds") int[] runIds, @JsonProperty("_sourceContainer") Container sourceContainer)
    {
        super();
        _runIds = runIds;
        _sourceContainer = sourceContainer;
    }

    public MoveRunsPipelineJob(ViewBackgroundInfo info, Container sourceContainer, int[] runIds, PipeRoot root)
    {
        super(ExperimentPipelineProvider.NAME, info, root);
        _runIds = runIds;
        _sourceContainer = sourceContainer;

        String baseLogFileName = FileUtil.makeFileNameWithTimestamp("moveRun", ".log");
        setupLocalDirectoryAndJobLog(getPipeRoot(), ExperimentService.MODULE_NAME, baseLogFileName);

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

    @Override
    protected String getDefaultLocalDirectoryString()
    {
        return getPipeRoot().isCloudRoot() ? FileUtil.getTempDirectory().getPath() : FileUtil.getAbsolutePath(_sourceContainer, ExperimentPipelineProvider.getMoveDirectory(getPipeRoot()));
    }

    @Override
    public String getDescription()
    {
        return "Move " + _runIds.length + " run" + (_runIds.length == 1 ? "" : "s") + " from " + _sourceContainer.getPath() + " to " + getContainer().getPath();
    }

    @Override
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

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(MoveRunsPipelineJob.class));
    }
}
