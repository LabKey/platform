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

package org.labkey.pipeline.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipelineRegistry;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <code>PipelineStatusManager</code> provides access to the StatusFiles table
 * in the Pipeline schema for recording status on <code>PipelineJob</code> automated
 * analysis.
 * 
 * @author brendanx
 */
public class PipelineStatusManager
{
    public static final DbScope.TransactionKind TRANSACTION_KIND = () -> "PIPELINESTATUS";

    private static PipelineSchema _schema = PipelineSchema.getInstance();
    private static final Logger LOG = LogManager.getLogger(PipelineStatusManager.class);

    public static TableInfo getTableInfo()
    {
        return _schema.getTableInfoStatusFiles();
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by RowId in the database.
     *
     * @param rowId id field
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    public static PipelineStatusFileImpl getStatusFile(int rowId)
    {
        return getStatusFile(new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by RowId in the database.
     *
     * @param rowId id field
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    public static List<PipelineStatusFileImpl> getStatusFiles(int... rowId)
    {
        ArrayList<Integer> ints = new ArrayList<>();
        for (int id : rowId)
            ints.add(id);

        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("RowId"), ints));
        return new TableSelector(_schema.getTableInfoStatusFiles(), filter, null).getArrayList(PipelineStatusFileImpl.class);
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the file path associated with the
     * entry.
     *
     * @param path file path to for the associated file
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    @Deprecated
    public static PipelineStatusFileImpl getStatusFile(File path)
    {
        return (path == null ? null :
                getStatusFile(new SimpleFilter(FieldKey.fromParts("FilePath"), PipelineJobService.statusPathOf(path.getAbsolutePath()))));
    }

    public static PipelineStatusFileImpl getStatusFile(Container container, Path path)
    {
        return (path == null ? null :
                getStatusFile(new SimpleFilter(FieldKey.fromParts("FilePath"),
                        PipelineJobService.statusPathOf(FileUtil.getUnencodedAbsolutePath(container, path)))));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the Job's id.
     *
     * @param jobId the job id for the associated job
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    public static PipelineStatusFileImpl getJobStatusFile(String jobId)
    {
        return (jobId == null ? null :
                getStatusFile(new SimpleFilter(FieldKey.fromParts("Job"), jobId)));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> using a specific filter.
     *
     * @param filter the filter to use in the select statement
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     */
    private static PipelineStatusFileImpl getStatusFile(Filter filter)
    {
        return new TableSelector(_schema.getTableInfoStatusFiles(), filter, null).getObject(PipelineStatusFileImpl.class);
    }

    public static boolean setStatusFile(PipelineJob job, User user, PipelineJob.TaskStatus status, @Nullable String info, boolean allowInsert)
    {
        return setStatusFile(job, user, status.toString(), info, allowInsert);
    }

    public static boolean setStatusFile(PipelineJob job, User user, String status, @Nullable String info, boolean allowInsert)
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(job.getJobGUID());
        if (sfExist == null && null != job.getLogFilePath())
        {
            // Then try based on file path
            sfExist = getStatusFile(job.getContainer(), job.getLogFilePath());
        }

        if (sfExist == null && job.getLogFilePath() != job.getRemoteLogPath() && null != job.getRemoteLogPath())
        {
            // Check to see if the job is listed under the Remote log file.
            // This will happen if job was previously run from a cloud root.
            sfExist = getStatusFile(job.getContainer(), job.getRemoteLogPath());
        }
        PipelineStatusFileImpl sfSet = new PipelineStatusFileImpl(job, status, info);

        if (null == sfExist)
        {
            if (allowInsert)
            {
                sfSet.beforeInsert(user, job.getContainerId());
                try (DbScope.Transaction transaction = getTableInfo().getSchema().getScope().ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
                {
                    // Use separate transaction/connection for TableInfoStatusFiles
                    PipelineStatusFileImpl sfNew = Table.insert(user, _schema.getTableInfoStatusFiles(), sfSet);
                    transaction.commit();

                    // Make sure rowID is correct, since it might be used in email.
                    sfSet.setRowId(sfNew.getRowId());
                }
            }
            else
            {
                job.getLogger().error("Could not find job in database for job GUID " + job.getJobGUID() + ", unable to set its status to '" + status + "'");
                return false;
            }
        }
        else
        {
            boolean cancelled = false;
            if (PipelineJob.TaskStatus.cancelling.matches(sfExist.getStatus()) && sfSet.isActive())
            {
                // Mark as officially dead
                sfSet.setStatus(PipelineJob.TaskStatus.cancelled.toString());
                cancelled = true;
            }
            sfSet.beforeUpdate(user, sfExist);
            updateStatusFile(sfSet);
            if (cancelled)
            {
                // Signal to the caller that the job shouldn't move on to its next state
                throw new CancelledException();
            }
        }

        if (isNotifyOnError(job) && PipelineJob.TaskStatus.error.matches(sfSet.getStatus()) &&
                (sfExist == null || !PipelineJob.TaskStatus.error.matches(sfExist.getStatus())))
        {
            LOG.info("Error status has changed - considering an email notification");
            PipelineManager.sendNotificationEmail(sfSet, job.getContainer(), user);
        }

        if (PipelineJob.TaskStatus.error.matches(status))
        {
            // Count this error on the job.
            job.setErrors(job.getErrors() + 1);

            // The non-enterprise pipeline will invoke this via JobRunner.afterExecute()
            if (PipelineService.get().isEnterprisePipeline())
            {
                job.done(null);
            }
        }
        else if (PipelineJob.TaskStatus.complete.matches(status))
        {
            // Make sure the Enterprise Pipeline recognizes this as a completed
            // job, even if did it not have a TaskPipeline.
            job.setActiveTaskId(null, false);

            // Notify if this is not a split job
            if (job.getParentGUID() == null)
                PipelineManager.sendNotificationEmail(sfSet, job.getContainer(), user);

            // The non-enterprise pipeline will invoke this via JobRunner.afterExecute()
            if (PipelineService.get().isEnterprisePipeline())
            {
                job.done(null);
            }
        }
        return true;
    }

    private static boolean isNotifyOnError(PipelineJob job)
    {
        return !job.isAutoRetry();
    }

    /**
     * Update status on a status file read from the database.
     *
     * @param sf the modified status
     */
    public static void updateStatusFile(PipelineStatusFileImpl sf)
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {
            // Use separate transaction/connection for TableInfoStatusFiles
            // Issue 19987 - If the job has been reparented on the web server and we're updating the status based
            // on a running job, it may not have the new parent job id. Check to make sure we're not trying to
            // point back at a defunct job, and restore the database record's notion of parent if we are.
            if (sf.getJobParentId() != null)
            {
                if (getJobStatusFile(sf.getJobParentId()) == null)
                {
                    PipelineStatusFileImpl databaseStatusFile = PipelineStatusManager.getStatusFile(sf.getRowId());
                    if (databaseStatusFile != null)
                    {
                        sf.setJobParent(databaseStatusFile.getJobParent());
                    }
                }
            }

            Table.update(null, _schema.getTableInfoStatusFiles(), sf, sf.getRowId());

            transaction.commit();
        }
    }

    /**
     * If there is an existing status entry for this file, make sure it has the
     * right job GUID, updating children as needed
     */
    public static void resetJobId(Container container, Path path, String jobId)
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {

            PipelineStatusFileImpl sfExist = getStatusFile(container, path);
            if (!jobId.equals(sfExist.getJob()))
            {
                List<PipelineStatusFileImpl> children = getSplitStatusFiles(sfExist.getJobId());
                for (PipelineStatusFileImpl child : children)
                {
                    LOG.debug("Resetting parent job ID for child job " + child.getRowId() + " - " + child.getFilePath());
                    child.setJobParent(null);
                    child.beforeUpdate(null, child);
                    updateStatusFile(child);
                }
                sfExist.setJob(jobId);
                sfExist.beforeUpdate(null, sfExist);
                updateStatusFile(sfExist);
                for (PipelineStatusFileImpl child : children)
                {
                    child.setJobParent(jobId);
                    child.beforeUpdate(null, child);
                    updateStatusFile(child);
                }
            }
            transaction.commit();
        }
    }

    public static void ensureError(PipelineJob job) throws NoSuchJobException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(job.getJobGUID());
        if (sfExist == null)
            throw new NoSuchJobException("Status for the job " + job.getJobGUID() + " was not found.");

        if (!PipelineJob.TaskStatus.error.matches(sfExist.getStatus()))
        {
            setStatusFile(job, job.getUser(), PipelineJob.TaskStatus.error, null, false);
        }
    }

    public static void storeJob(String jobId, String xml) throws NoSuchJobException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            throw new NoSuchJobException("Status for the job " + jobId + " was not found.");

        try (DbScope.Transaction transaction = getTableInfo().getSchema().getScope().ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {
            // Use separate transaction/connection for TableInfoStatusFiles
            String sql = "UPDATE " + _schema.getTableInfoStatusFiles() +
                    " SET JobStore = ?" +
                    " WHERE RowId = ?";
            new SqlExecutor(_schema.getSchema()).execute(sql, xml, sfExist.getRowId());
            transaction.commit();
        }
    }

    public static String retrieveJob(int rowId)
    {
        PipelineStatusFileImpl sfExist = getStatusFile(rowId);
        if (sfExist == null)
            return null;

        return retrieveJob(sfExist);
    }

    @Nullable
    public static String retrieveJob(String jobId)
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            return null;
        
        return retrieveJob(sfExist);
    }

    private static String retrieveJob(PipelineStatusFileImpl sfExist)
    {
        try (DbScope.Transaction transaction = getTableInfo().getSchema().getScope().ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {
            // Use separate transaction/connection for TableInfoStatusFiles
            String sql = "UPDATE " + _schema.getTableInfoStatusFiles() +
                    " SET JobStore = NULL" +
                    " WHERE RowId = ?";
            new SqlExecutor(_schema.getSchema()).execute(sql, sfExist.getRowId());
            transaction.commit();
        }

        return sfExist.getJobStore();
    }

    public static List<PipelineStatusFileImpl> getSplitStatusFiles(String parentId)
    {
        if (parentId == null)
        {
            return Collections.emptyList();
        }
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("JobParent"), parentId, CompareType.EQUAL);

        return getStatusFiles(filter);
    }

    /**
    * Returns a count of jobs not marked COMPLETE which were created by splitting another job.
    *
    * @param parentId the jobGUID for the joined task that created split tasks
    * @param container the container where the joined task is defined
    * @return int count of <code>PipelineStatusFiles<code> not marked COMPLETE
    */
    public static int getIncompleteStatusFileCount(String parentId, Container container)
    {
        return new SqlSelector(_schema.getSchema(), "SELECT COUNT(*) FROM " + _schema.getTableInfoStatusFiles() + " WHERE Container = ? AND JobParent = ? AND Status <> ?",
                container, parentId, PipelineJob.TaskStatus.complete.toString()).getObject(Integer.class);
    }

    public static List<PipelineStatusFileImpl> getStatusFilesForLocation(String location, boolean includeJobsOnQueue)
    {
        // NOTE: JobIds end up all uppercase in the database, but they are lowercase in jobs
        Set<String> ignorableIds = new CaseInsensitiveHashSet();
        if (!includeJobsOnQueue)
        {
            List<PipelineJob> queuedJobs = PipelineService.get().getPipelineQueue().findJobs(location);
            for (PipelineJob job : queuedJobs)
            {
                ignorableIds.add(job.getJobGUID());
            }
        }

        List<PipelineStatusFileImpl> result = new ArrayList<>();
        TaskPipelineRegistry registry = PipelineJobService.get();
        for (TaskFactory taskFactory : registry.getTaskFactories(null))
        {
            if (taskFactory.getExecutionLocation().equals(location))
            {
                TaskId id = taskFactory.getId();
                for (PipelineStatusFileImpl statusFile : getQueuedStatusFilesForActiveTaskId(id.toString()))
                {
                    if (!ignorableIds.contains(statusFile.getJobId()))
                    {
                        result.add(statusFile);
                    }
                }
            }
        }
        return result;
    }

    public static List<PipelineStatusFileImpl> getQueuedStatusFilesForActiveTaskId(String activeTaskId)
    {
        SimpleFilter filter = createQueueFilter();
        filter.addCondition(FieldKey.fromParts("ActiveTaskId"), activeTaskId, CompareType.EQUAL);

        return getStatusFiles(filter);
    }

    public static List<PipelineStatusFileImpl> getQueuedStatusFilesForContainer(Container c)
    {
        SimpleFilter filter = createQueueFilter();
        filter.addCondition(FieldKey.fromParts("Container"), c, CompareType.EQUAL);

        return getStatusFiles(filter);
    }

    public static List<PipelineStatusFileImpl> getJobsWaitingForFiles(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Status"), PipelineJob.TaskStatus.waitingForFiles.toString());

        return getStatusFiles(filter);
    }

    public static List<PipelineStatusFileImpl> getQueuedStatusFiles()
    {
        SimpleFilter filter = createQueueFilter();
        
        return getStatusFiles(filter);
    }

    private static List<PipelineStatusFileImpl> getStatusFiles(SimpleFilter filter)
    {
        return new TableSelector(_schema.getTableInfoStatusFiles(), filter, null).getArrayList(PipelineStatusFileImpl.class);
    }

    private static SimpleFilter createQueueFilter()
    {
        SimpleFilter filter = new SimpleFilter();
        for (PipelineJob.TaskStatus status : PipelineJob.TaskStatus.values())
        {
            if (!status.isActive())
            {
                filter.addCondition(FieldKey.fromParts("Status"), status.toString(), CompareType.NEQ);
            }
        }
        filter.addCondition(FieldKey.fromParts("Job"), null, CompareType.NONBLANK);
        return filter;
    }

    public static void completeStatus(User user, Collection<Integer> rowIds)
    {
        // Make entire transaction use the PipelineStatus connection, since Exp.Data/Exp.ExperimentRun are tied to Pipeline.StatusFiles
        try (DbScope.Transaction transaction = PipelineSchema.getInstance().getSchema().getScope().ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {
            for (int rowId : rowIds)
            {
                boolean statusSet = false;
                PipelineJob job = PipelineJobService.get().getJobStore().getJob(rowId);

                if (job != null)
                {
                    job.info("Job " + job + " was marked as complete by " + user + ". It will run in its turn; it cannot be canceled, but can be deleted.");
                    if (!job.getContainer().hasPermission(user, UpdatePermission.class))
                    {
                        throw new UnauthorizedException();
                    }

                    setStatusFile(job, user, PipelineJob.TaskStatus.complete, null, false);
                    statusSet = true;
                }

                if (!statusSet)
                {
                    // Fall back to updating the simple bean in the case where can can't deserialize the job itself
                    PipelineStatusFileImpl sf = PipelineStatusManager.getStatusFile(rowId);
                    if (sf != null)
                    {
                        LOG.info("Job " + sf.getFilePath() + " was marked as complete by " + user);
                        sf.setStatus(PipelineJob.TaskStatus.complete.toString());
                        sf.setInfo(null);
                        PipelineStatusManager.updateStatusFile(sf);
                    }
                }
            }
            transaction.commit();
        }
    }

    public static void deleteStatus(Container c, User u, boolean deleteExpRuns, Collection<Integer> rowIds) throws PipelineProvider.HandlerException
    {
        // Make entire transaction use the PipelineStatus connection, since Exp.Data/Exp.ExperimentRun are tied to Pipeline.StatusFiles
        try (DbScope.Transaction transaction = _schema.getSchema().getScope().ensureTransaction(PipelineStatusManager.TRANSACTION_KIND))
        {
            Set<Integer> ids = new HashSet<>(rowIds);
            deleteStatus(c, u, deleteExpRuns, ids);
            if (!ids.isEmpty())
            {
                throw new PipelineProvider.HandlerException("Failed to delete " + ids.size() + " job" + (ids.size() > 1 ? "s" : ""));
            }
            transaction.commit();
        }
    }

    private static void deleteStatus(Container container, User user, boolean deleteExpRuns, Set<Integer> rowIds)
    {
        assert _schema.getSchema().getScope().isTransactionActive() : "Should only be invoked inside of a transaction";
        if (rowIds.isEmpty())
        {
            return;
        }

        // Use a set instead of a list since the incoming set of rowIds may contain child and parent jobs, and
        // we don't want to double-log the deletion of the child jobs
        Set<PipelineStatusFile> deleteable = new HashSet<>();
        for (int rowId : rowIds)
        {
            PipelineStatusFile sf = getStatusFile(rowId);

            // First check that it still exists in the database and that it isn't running anymore
            if (sf != null && !sf.isActive())
            {
                Container targetContainer = sf.lookupContainer();
                if (!targetContainer.hasPermission(user, DeletePermission.class))
                {
                    throw new UnauthorizedException();
                }
                // Check if the job has any children
                List<PipelineStatusFileImpl> children = PipelineStatusManager.getSplitStatusFiles(sf.getJobId());
                boolean hasActiveChildren = false;
                for (PipelineStatusFileImpl child : children)
                {
                    hasActiveChildren |= child.isActive();
                }

                if (!hasActiveChildren)
                {
                    if (children.isEmpty())
                    {
                        deleteable.add(sf);
                    }
                    else
                    {
                        // Delete the children first and let the recursion delete the parent.
                        deleteable.addAll(children);
                    }
                }
            }
        }

        if (!deleteable.isEmpty())
        {
            // Delete the associated ExpRuns.
            // The runs must be deleted before the provider's preDeleteStatusFile is called
            if (deleteExpRuns)
                deleteAssocatedRuns(container, user, deleteable);

            SQLFragment sql = new SQLFragment();
            sql.append("DELETE FROM ").append(_schema.getTableInfoStatusFiles())
                    .append(" ").append("WHERE RowId ");

            // null any ExpRuns referencing these Jobs -- only executed if we aren't deleting the runs
            SQLFragment expSql = new SQLFragment();
            expSql.append("UPDATE ").append(ExperimentService.get().getTinfoExperimentRun())
                    .append(" SET JobId = NULL ")
                    .append("WHERE JobId ");

            List<Integer> statusFileIds = new ArrayList<>();
            for (PipelineStatusFile pipelineStatusFile : deleteable)
            {
                Container targetContainer = pipelineStatusFile.lookupContainer();
                if (targetContainer == null || !targetContainer.hasPermission(user, DeletePermission.class))
                {
                    throw new UnauthorizedException("No permission to delete job from " + targetContainer);
                }
                // Allow the provider to do any necessary clean-up
                PipelineProvider provider = PipelineService.get().getPipelineProvider(pipelineStatusFile.getProvider());
                if (provider != null)
                    provider.preDeleteStatusFile(user, pipelineStatusFile);

                LOG.info("Job " + pipelineStatusFile.getFilePath() + " was deleted by " + user);
                statusFileIds.add(pipelineStatusFile.getRowId());
            }
            _schema.getSqlDialect().appendInClauseSql(sql, statusFileIds);
            _schema.getSqlDialect().appendInClauseSql(expSql, statusFileIds);

            // Remember that we deleted these rows
            rowIds.removeAll(statusFileIds);

            if (!container.isRoot())
            {
                // Use a ContainerFilter to generate the SQL so that we include workbooks - see issue 22236
                SQLFragment containerSQL = ContainerFilter.current(container).getSQLFragment(PipelineSchema.getInstance().getSchema(), new SQLFragment("Container"));
                sql.append(" AND ");
                sql.append(containerSQL);
                expSql.append(" AND ");
                expSql.append(containerSQL);
            }

            if (!deleteExpRuns)
            {
                // Not deleting runs: null any ExpRuns referencing these Jobs if we aren't deleting them
                new SqlExecutor(ExperimentService.get().getSchema()).execute(expSql);
            }

            int rowCount = new SqlExecutor(_schema.getSchema()).execute(sql);

            // If we deleted anything, try recursing since we may have deleted all the child jobs which would
            // allow a parent job to be deleted
            if (rowCount > 0 && !rowIds.isEmpty())
            {
                deleteStatus(container, user, deleteExpRuns, rowIds);
            }
        }
    }

    private static void deleteAssocatedRuns(Container container, User user, Set<PipelineStatusFile> deleteable)
    {
        for (PipelineStatusFile pipelineStatusFile : deleteable)
        {
            // Get the list of runs that reference this job
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("jobid"), pipelineStatusFile.getRowId());
            TableInfo runsTable = ExperimentService.get().getTinfoExperimentRun();
            List<Integer> runIds = new TableSelector(runsTable, Collections.singleton("rowid"), filter, null).getArrayList(Integer.class);

            // Convert to varargs
            int[] ints = new int[runIds.size()];
            int i = 0;
            for (Integer runId : runIds)
                ints[i++] = runId;
            ExperimentService.get().deleteExperimentRunsByRowIds(container, user, ints);
        }
    }

    /**
     * Cancel all pending and running jobs in the container.
     */
    public static void cancelStatus(ViewBackgroundInfo info) throws PipelineProvider.HandlerException
    {
        // Get waiting or running jobs
        SimpleFilter filter = SimpleFilter.createContainerFilter(info.getContainer());
        filter.addCondition(FieldKey.fromParts("Status"), Arrays.asList(PipelineJob.TaskStatus.running.name(), PipelineJob.TaskStatus.waiting.name()), CompareType.IN);

        int failed = 0;
        for (PipelineStatusFileImpl statusFile : PipelineStatusManager.getStatusFiles(filter))
        {
            if (!cancelStatus(info, statusFile))
                failed++;
        }
        if (failed == 1)
        {
            throw new PipelineProvider.HandlerException("Unable to cancel job - it may already be COMPLETE, ERROR, or CANCELLED");
        }
        else if (failed > 1)
        {
            throw new PipelineProvider.HandlerException("Unable to cancel " + failed + " jobs - they may already be COMPLETE, ERROR, or CANCELLED");
        }
    }

    public static void cancelStatus(ViewBackgroundInfo info, Collection<Integer> rowIds) throws PipelineProvider.HandlerException
    {
        if (rowIds.isEmpty())
        {
            return;
        }

        int failed = 0;
        for (int rowId : rowIds)
        {
            PipelineStatusFileImpl statusFile = PipelineStatusManager.getStatusFile(rowId);
            if (statusFile == null)
            {
                throw new NotFoundException();
            }
            if (!cancelStatus(info, statusFile))
            {
                failed++;
            }
        }
        if (failed == 1)
        {
            throw new PipelineProvider.HandlerException("Unable to cancel job - it may already be COMPLETE, ERROR, or CANCELLED");
        }
        else if (failed > 1)
        {
            throw new PipelineProvider.HandlerException("Unable to cancel " + failed + " jobs - they may already be COMPLETE, ERROR, or CANCELLED");
        }
    }

    private static boolean cancelStatus(ViewBackgroundInfo info, PipelineStatusFileImpl statusFile)
    {
        Container jobContainer = statusFile.lookupContainer();
        if (jobContainer == null || !jobContainer.hasPermission(info.getUser(), DeletePermission.class))
        {
            throw new UnauthorizedException();
        }
        if (statusFile.isCancellable())
        {
            for (PipelineStatusFileImpl child : PipelineStatusManager.getSplitStatusFiles(statusFile.getJobId()))
            {
                if (child.isCancellable())
                {
                    cancelStatus(info, child);
                }
            }

            PipelineJob.TaskStatus newStatus;
            if (PipelineJob.TaskStatus.splitWaiting.matches(statusFile.getStatus()) || PipelineJob.TaskStatus.waitingForFiles.matches(statusFile.getStatus()))
            {
                newStatus = PipelineJob.TaskStatus.cancelled;
            }
            else
            {
                newStatus = PipelineJob.TaskStatus.cancelling;
            }
            statusFile.setStatus(newStatus.toString());
            PipelineStatusManager.updateStatusFile(statusFile);
            PipelineService.get().getPipelineQueue().cancelJob(info.getUser(), jobContainer, statusFile);
            return true;
        }
        else
        {
            return false;
        }
    }
}
