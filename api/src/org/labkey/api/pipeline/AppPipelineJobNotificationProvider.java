package org.labkey.api.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.assay.pipeline.AssayUploadPipelineJob;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.QueryImportPipelineJob;
import org.labkey.api.security.SecurityManager;

import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract public class AppPipelineJobNotificationProvider implements PipelineJobNotificationProvider
{
    public enum ImportType {
        samples,
        sources,
        assays;

        public static ImportType getImportType(PipelineJob job)
        {
            if (job instanceof QueryImportPipelineJob)
            {
                QueryImportPipelineJob queryImportPipelineJob = (QueryImportPipelineJob) job;
                String schemaName = queryImportPipelineJob.getImportContextBuilder().getSchemaName();
                if (schemaName.equalsIgnoreCase("samples"))
                    return samples;
                else if (schemaName.equalsIgnoreCase("exp.data"))
                    return sources;
            }
            else if (job instanceof AssayUploadPipelineJob)
            {
                return assays;
            }

            return null;
        }
    }

    public enum ImportNotify
    {
        Start, Success, Error
    }

    protected abstract ActionURL getAppURL(Container c);

    @Override
    public void onJobQueued(PipelineJob job)
    {
        ImportType importType = ImportType.getImportType(job);
        if (importType == null)
            return;

        // notify all user sessions
        notifyJobStatusChange(job.getContainer(), ImportNotify.Start);
    }

    @Override
    public void onJobSuccess(PipelineJob job, @Nullable Map<String, Object> info)
    {
        ImportType importType = ImportType.getImportType(job);
        if (importType == null)
            return;

        // send job owner notification
        sendJobNotification(job, importType, true, info, null);

        // notify all user sessions
        notifyJobStatusChange(job.getContainer(), ImportNotify.Success);
    }

    @Override
    public void onJobError(PipelineJob job, String errorMsg)
    {
        ImportType importType = ImportType.getImportType(job);
        if (importType == null)
            return;

        // send job owner notification
        sendJobNotification(job, importType, false, null, errorMsg);

        // notify all user sessions
        notifyJobStatusChange(job.getContainer(), ImportNotify.Error);
    }

    @Override
    public void onJobDone(PipelineJob job)
    {
        // do nothing, notifications are handled in success and error separately
    }

    @Override
    public boolean useDefaultJobNotification(PipelineJob job)
    {
        return ImportType.getImportType(job) == null;
    }

    @Override
    public URLHelper getPipelineStatusHref(PipelineJob job)
    {
        if (job instanceof QueryImportPipelineJob)
        {
            ImportType importType = ImportType.getImportType(job);
            if (importType == null)
                return null;

            String urlFragment = "/" + importType.name();

            ActionURL appURL = getAppURL(job.getContainer());
            QueryImportPipelineJob queryImportPipelineJob = (QueryImportPipelineJob) job;

            String type = queryImportPipelineJob.getImportContextBuilder().getQueryName();
            urlFragment += "/" + type + "?";

            String and = "";

            if (queryImportPipelineJob.getTransactionAuditId() > 0)
            {
                urlFragment = urlFragment + "transactionAuditId=" + queryImportPipelineJob.getTransactionAuditId();
                and = "&";
            }

            String filename = queryImportPipelineJob.getImportContextBuilder().getPrimaryFile().getName();
            if (!StringUtils.isEmpty(filename))
                urlFragment = urlFragment + and + "importFile=" + filename;

            return appURL.setFragment(urlFragment);
        }

        return null;
    }

    private void notifyJobStatusChange(Container container, ImportNotify status)
    {
        List<User> projectUsers = SecurityManager.getUsersWithPermissions(container, Collections.singleton(ReadPermission.class));

        List<Integer> userIds = new ArrayList<>();
        projectUsers.forEach(user -> userIds.add(user.getUserId()));
        NotificationService.get().sendServerEvent(userIds, status);
    }

    private String getJobSuccessMsg(PipelineJob job, @NotNull ImportType importType, @Nullable Map<String, Object> info)
    {
        if (job instanceof QueryImportPipelineJob)
        {
            QueryImportPipelineJob queryImportPipelineJob = (QueryImportPipelineJob) job;

            String type = queryImportPipelineJob.getImportContextBuilder().getQueryName();
            StringBuilder successMsg = new StringBuilder("Successfully imported ");
            if (info != null)
            {
                Integer count =  (Integer) info.get("rowCount");
                if (count != null)
                {
                    successMsg.append(count).append(" ");
                }
            }

            successMsg
                    .append(type)
                    .append(" ")
                    .append(importType.name())
                    .append(" from ")
                    .append(queryImportPipelineJob.getImportContextBuilder().getPrimaryFile().getName());

            return successMsg.toString();
        }
        else if (job instanceof AssayUploadPipelineJob)
        {
            String successMsg = "Successfully imported assay run";

            if (info != null)
            {
                String assayName = (String) info.get("assayName");

                String fiename = ((AssayUploadPipelineJob) job).getPrimaryFile().getName();
                if (!fiename.endsWith(".tmp"))
                {
                    successMsg += " from " + fiename;
                }
                successMsg += " for " + assayName;
            }
            return successMsg;
        }

        return null;
    }

    private String getJobErrorMsg(PipelineJob job, String rawErrorMsg)
    {
        if (job instanceof QueryImportPipelineJob)
        {
            QueryImportPipelineJob queryImportPipelineJob = (QueryImportPipelineJob) job;

            String type = queryImportPipelineJob.getImportContextBuilder().getQueryName();

            return "Failed to import " +
                    type +
                    " from " +
                    queryImportPipelineJob.getImportContextBuilder().getPrimaryFile().getName() +
                    "\n" +
                    rawErrorMsg;// resolveErrorMessage on client
        }
        else if (job instanceof AssayUploadPipelineJob)
        {
            return "Failed to import assay run from " +
                    ((AssayUploadPipelineJob) job).getPrimaryFile().getName() +
                    "\n" +
                    rawErrorMsg;
        }

        return null;
    }

    private String getJobSuccessUrl(PipelineJob job, @NotNull ImportType importType, @Nullable Map<String, Object> info)
    {
        ActionURL appURL = getAppURL(job.getContainer());
        String urlFragment = "/" + importType.name();
        if (job instanceof QueryImportPipelineJob)
        {
            QueryImportPipelineJob queryImportPipelineJob = (QueryImportPipelineJob) job;

            String type = queryImportPipelineJob.getImportContextBuilder().getQueryName();
            urlFragment += "/" + type + "?";

            String and = "";
            if (info != null)
            {
                Long transactionAuditId = (Long) info.get("transactionAuditId");
                urlFragment = urlFragment + "transactionAuditId=" + transactionAuditId ;
                and = "&";
            }

            String filename = queryImportPipelineJob.getImportContextBuilder().getPrimaryFile().getName();
            if (!StringUtils.isEmpty(filename))
                urlFragment = urlFragment + and + "importFile=" + filename;

        }
        else if (job instanceof AssayUploadPipelineJob)
        {
            if (info != null)
            {
                String provider = (String) info.get("provider");
                String assayName = (String) info.get("assayName");
                int runId = (int) info.get("runId");
                urlFragment = urlFragment + "/" + provider + "/" + assayName + "/runs/" + runId;
            }
        }

        return appURL.setFragment(urlFragment).getLocalURIString();
    }

    private String getJobErrorUrl(PipelineJob job)
    {
        ActionURL appURL = getAppURL(job.getContainer());
        String urlFragment = "/pipeline";
        Integer jobId = PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getJobGUID());
        if (jobId != null)
            urlFragment = urlFragment + "/" + jobId;

        return appURL.setFragment(urlFragment).getLocalURIString();
    }

    public void sendJobNotification(PipelineJob job, @NotNull ImportType importType, boolean isSuccess, @Nullable Map<String, Object> jobInfo, @Nullable String msgContent)
    {
        User user = job.getUser();
        PipelineJob.TaskStatus status = job.getActiveTaskStatus();
        Notification n = new Notification(job.getJobGUID(), status.getNotificationType(), user);

        if (isSuccess)
        {
            n.setContent(getJobSuccessMsg(job, importType, jobInfo), "text/plain");
            n.setActionLinkURL(getJobSuccessUrl(job, importType, jobInfo));
            n.setActionLinkText("view imported " + importType.name());
        }
        else
        {
            n.setContent(getJobErrorMsg(job, msgContent), "text/plain");
            n.setActionLinkURL(getJobErrorUrl(job));
            n.setActionLinkText("view error details");
        }

        // don't attempt to add a notification if the Container has been deleted or is deleting
        if (ContainerManager.getForId(job.getContainerId()) == null || ContainerManager.isDeleting(job.getContainer()))
        {
            job.getLogger().info("Job container has been deleted or is being deleted; skipping notification for '" + StringUtils.defaultString(job.getDescription(), job.toString()) + "'");
            return;
        }

        DefaultPipelineJobNotificationProvider.sendJobNotification(job, n);
    }
}
