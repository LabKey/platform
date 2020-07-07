/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.pipeline.status.StatusController;
import org.mule.util.StringUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: 2/10/14
 */
public abstract class AbstractPipelineQueue implements PipelineQueue
{
    private static final Logger LOG = LogManager.getLogger(AbstractPipelineQueue.class);

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

    protected void notifyDone(PipelineJob job)
    {
        User user = job.getUser();
        if (null == user || user.isServiceUser() || user.getUserId() <= 0)
            return;
        if (null == job.getJobGUID())
            return;

        // don't attempt to add a notification if the Container has been deleted or is deleting
        if (ContainerManager.getForId(job.getContainerId()) == null || ContainerManager.isDeleting(job.getContainer()))
        {
            LOG.info("Job container has been deleted or is being deleted; skipping notification for '" + StringUtils.defaultString(job.getDescription(), job.toString()) + "'");
            return;
        }

        try
        {
            PipelineJob.TaskStatus status = job.getActiveTaskStatus();
            Notification n = new Notification(job.getJobGUID(), status.getNotificationType(), user);
            String description = StringUtils.defaultString(job.getDescription(), job.toString());
            n.setContent(String.format("Background job %s\n%s", status.toString().toLowerCase(), description), "text/plain");
            if (null != job.getStatusHref())
            {
                n.setActionLinkURL(job.getStatusHref().getLocalURIString());
                n.setActionLinkText("view");
            }
            else
            {
                Integer jobId = PipelineService.get().getJobId(user, job.getContainer(), job.getJobGUID());
                if (jobId != null)
                {
                    n.setActionLinkURL(new ActionURL(StatusController.DetailsAction.class, job.getContainer()).addParameter("rowId", jobId).getLocalURIString());
                    n.setActionLinkText("view");
                }
                else
                {
                    n.setActionLinkURL(new ActionURL(StatusController.ShowListAction.class, job.getContainer()).getLocalURIString());
                    n.setActionLinkText("pipeline");
                }
            }
            // Remove all previous notifications for this job
            NotificationService.get().removeNotifications(
                    job.getContainer(),
                    job.getJobGUID(),
                    Arrays.stream(PipelineJob.TaskStatus.values()).map(PipelineJob.TaskStatus::getNotificationType).collect(Collectors.toList()),
                    user.getUserId());
            NotificationService.get().addNotification(job.getContainer(), user, n);
        }
        catch (ValidationException x)
        {
            LOG.warn("Notification error", x);
        }
    }
}
