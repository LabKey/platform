/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
import org.labkey.api.util.FileType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;

import java.util.List;
import java.util.Collections;

/**
 * User: jeckels
 * Date: Sep 8, 2008
 */
public class MoveRunsTaskFactory extends AbstractTaskFactory<AbstractTaskFactorySettings, MoveRunsTaskFactory>
{
    public MoveRunsTaskFactory()
    {
        super(MoveRunsTask.class);
    }

    public PipelineJob.Task createTask(PipelineJob job)
    {
        if (!(job instanceof MoveRunsPipelineJob))
        {
            throw new IllegalArgumentException("Only supported in the context of a MoveRunsPipelineJob");
        }
        return new MoveRunsTask(this, job);
    }

    public List<FileType> getInputTypes()
    {
        return Collections.emptyList();
    }

    public List<String> getProtocolActionNames()
    {
        return Collections.emptyList();
    }

    public String getStatusName()
    {
        return "MOVE RUNS";
    }

    public boolean isJobComplete(PipelineJob j)
    {
        MoveRunsPipelineJob job = (MoveRunsPipelineJob)j;
        for (int runId : job.getRunIds())
        {
            ExpRun run = ExperimentService.get().getExpRun(runId);
            if (run != null && !run.getContainer().equals(j.getContainer()))
            {
                return false;
            }
        }
        return true;
    }
}