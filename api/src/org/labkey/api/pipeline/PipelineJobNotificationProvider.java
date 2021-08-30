package org.labkey.api.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public interface PipelineJobNotificationProvider
{
    @NotNull String getName();

    default void onJobQueued(PipelineJob job)
    {

    }

    default void onJobStart(PipelineJob job)
    {

    }

    default void onJobSuccess(PipelineJob job)
    {
        onJobSuccess(job, null);
    }

    /**
     * @param job
     * @param info Allow passing results (such as imported rows, transaction id) of the run to provider
     */
    default void onJobSuccess(PipelineJob job, @Nullable Map<String, Object> info)
    {

    }

    default void onJobError(PipelineJob job)
    {
        onJobError(job, null);
    }

    default void onJobError(PipelineJob job, String errorMsg)
    {

    }

    default void onJobDone(PipelineJob job)
    {

    }

    default URLHelper getPipelineStatusHref(PipelineJob job)
    {
        return null;
    }

    // allow provider to fall back to the default notification for specific jobs
    //
    default boolean useDefaultJobNotification(PipelineJob job)
    {
        return false;
    }

    class DefaultPipelineJobNotificationProvider implements PipelineJobNotificationProvider
    {
        public static final String DEFAULT_PIPELINE_JOB_NOTIFICATION_PROVIDER = "default";
        @Override
        public @NotNull String getName()
        {
            return DEFAULT_PIPELINE_JOB_NOTIFICATION_PROVIDER;
        }

        @Override
        public void onJobDone(PipelineJob job)
        {
            sendJobNotification(job, getJobNotification(job, null));
        }

        public static Notification getJobNotification(PipelineJob job, @Nullable String msgContent)
        {
            if (!canSendNotification(job))
                return null;

            User user = job.getUser();
            PipelineJob.TaskStatus status = job.getActiveTaskStatus();

            Notification n = new Notification(job.getJobGUID(), job.getNotificationType(status), user);
            if (StringUtils.isEmpty(msgContent))
            {
                String description = StringUtils.defaultString(job.getDescription(), job.toString());
                n.setContent(String.format("Background job %s\n%s", status.toString().toLowerCase(), description), "text/plain");
            }
            else
                n.setContent(msgContent);

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
                    n.setActionLinkURL(PageFlowUtil.urlProvider(PipelineUrls.class).statusDetails(job.getContainer(), jobId).getLocalURIString());
                    n.setActionLinkText("view");
                }
                else
                {
                    n.setActionLinkURL(PageFlowUtil.urlProvider(PipelineUrls.class).statusList(job.getContainer()).getLocalURIString());
                    n.setActionLinkText("pipeline");
                }
            }

            return n;
        }

        private static boolean canSendNotification(PipelineJob job)
        {
            User user = job.getUser();
            if (null == user || user.isServiceUser() || user.getUserId() <= 0)
                return false;
            if (null == job.getJobGUID())
                return false;

            // don't attempt to add a notification if the Container has been deleted or is deleting
            if (ContainerManager.getForId(job.getContainerId()) == null || ContainerManager.isDeleting(job.getContainer()))
            {
                job.getLogger().info("Job container has been deleted or is being deleted; skipping notification for '" + StringUtils.defaultString(job.getDescription(), job.toString()) + "'");
                return false;
            }

            return true;
        }

        public static void sendJobNotification(PipelineJob job, @Nullable Notification n)
        {
            if (n == null)
                return;

            User user = job.getUser();

            try
            {
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
                job.getLogger().warn("Notification error", x);
            }

        }

    }

}
