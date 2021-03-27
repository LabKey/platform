/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.study.visitmanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.util.TimerTask;

/**
 * Background task to remove participants that are no longer referenced in datasets or specimen data.
 * Created by gktaylor on 4/29/14.
 */
public class PurgeParticipantsTask extends TimerTask
{
    private static final Logger LOG = LogManager.getLogger(PurgeParticipantsTask.class);

    public static volatile boolean JOB_QUEUED = false;

    public PurgeParticipantsTask()
    {
    }

    @Override
    public void run()
    {
        // Don't bother queueing a job if map is empty or job is already in the queue
        synchronized (VisitManager.POTENTIALLY_DELETED_PARTICIPANTS)
        {
            if (VisitManager.POTENTIALLY_DELETED_PARTICIPANTS.isEmpty() || JOB_QUEUED)
                return;
        }

        Container c = ContainerManager.getRoot();
        ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, null, null);
        PipeRoot root = PipelineService.get().findPipelineRoot(c);

        if (null == root)
            throw new ConfigurationException("Invalid pipeline configuration at the root container");

        if (!root.isValid())
            throw new ConfigurationException("Invalid pipeline configuration at the root container: " + root.getRootPath().getPath());

        try
        {
            // Queue a pipeline job to prevent participant purge from running in parallel with study import, #42641
            PipelineJob job = new PurgeParticipantsJob(vbi, root);
            LOG.debug("Queuing PurgeParticipantsJob [thread " + Thread.currentThread().getName() + " to " + PipelineService.get().toString() + "]");
            PipelineService.get().queueJob(job);
            JOB_QUEUED = true;
        }
        catch (PipelineValidationException e)
        {
            throw new RuntimeException(e);
        }
    }
}
