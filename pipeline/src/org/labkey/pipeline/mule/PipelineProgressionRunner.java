/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

/**
 * <code>PipelineProgressionRunner</code> is a Mule object for advancing a
 * <code>PipelineJob</code> to the next task in the task progression of its
 * <code>TaskPipeline</code>.
 *
 * @author brendanx
 */
public class PipelineProgressionRunner
{
    public void run(PipelineJob job)
    {
        // Double check that the job hasn't been cancelled or otherwise deleted
        PipelineStatusFileImpl statusFile = PipelineStatusManager.getJobStatusFile(job.getJobGUID());
        if (statusFile == null || PipelineJob.TaskStatus.cancelled.matches(statusFile.getStatus()) || PipelineJob.TaskStatus.cancelling.matches(statusFile.getStatus()))
        {
            return;
        }
        job.runStateMachine();
    }
}