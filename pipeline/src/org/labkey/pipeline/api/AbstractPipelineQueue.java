/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;

import java.nio.file.Path;

/**
 * User: jeckels
 * Date: 2/10/14
 */
public abstract class AbstractPipelineQueue implements PipelineQueue
{
    private static final Logger LOG = Logger.getLogger(AbstractPipelineQueue.class);

    @Override
    public void addJob(final PipelineJob job) throws PipelineValidationException
    {
        if (null == job)
            throw new NullPointerException();

        job.validateParameters();
        User user = job.getUser();
        if (user == null)
        {
            throw new PipelineValidationException("No User associated with job " + job);
        }
        if (!user.isActive())
        {
            throw new PipelineValidationException("The account for user " + user + " is not active");
        }
        // Treat the guest user like a system account.
        // Important for some unit tests, like PipelineQueueImpl.TestCase.testPipeline()
        if (!user.isGuest() && !job.getContainer().hasPermission(user, ReadPermission.class))
        {
            throw new PipelineValidationException("User " + user + " does not have access to " + job.getContainer());
        }

        LOG.debug("PENDING: " + job.toString());

        // Make sure status file path and Job ID are in sync.
        Path logFile = job.getLogFilePath();
        if (logFile != null)
        {
            PipelineStatusFileImpl pipelineStatusFile = PipelineStatusManager.getStatusFile(job.getContainer(), logFile);
            if (pipelineStatusFile == null)
            {
                PipelineStatusManager.setStatusFile(job, user, PipelineJob.TaskStatus.waiting, null, true);
            }

            PipelineStatusManager.resetJobId(job.getContainer(), job.getLogFilePath(), job.getJobGUID());
        }

        if (job.setQueue(this, PipelineJob.TaskStatus.waiting))
        {
            // Delay until the transaction has been committed so other threads can find the job in the database
            PipelineSchema.getInstance().getSchema().getScope().addCommitTask(() -> enqueue(job), DbScope.CommitTaskOption.POSTCOMMIT);
        }
    }

    protected abstract void enqueue(PipelineJob job);
}
