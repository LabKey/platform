/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.pipeline.*;
import org.labkey.api.util.CaseInsensitiveHashSet;

import java.sql.SQLException;
import java.util.*;
import java.io.IOException;

/**
 * <code>PipelineStatusManager</code> provides access to the StatusFiles table
 * in the Pipeline schema for recording status on <code>PipelineJob</code> automated
 * analysis.
 * 
 * @author brendanx
 */
public class PipelineStatusManager
{
    private static PipelineSchema pipeline = PipelineSchema.getInstance();

    public static TableInfo getTableInfo()
    {
        return pipeline.getTableInfoStatusFiles();
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by RowId in the database.
     *
     * @param rowId id field
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getStatusFile(int rowId) throws SQLException
    {
        return getStatusFile(new SimpleFilter("RowId", new Integer(rowId)));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the file path associated with the
     * entry.
     *
     * @param path file path to for the associated file
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getStatusFile(String path) throws SQLException
    {
        return (path == null ? null :
                getStatusFile(new SimpleFilter("FilePath", PipelineJobService.statusPathOf(path))));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> by the Job's id.
     *
     * @param jobId the job id for the associated job
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl getJobStatusFile(String jobId) throws SQLException
    {
        return (jobId == null ? null :
                getStatusFile(new SimpleFilter("Job", jobId)));
    }

    /**
     * Get a <code>PipelineStatusFileImpl</code> using a specific filter.
     *
     * @param filter the filter to use in the select statement
     * @return the corresponding <code>PipelineStatusFileImpl</code>
     * @throws SQLException database error
     */
    private static PipelineStatusFileImpl getStatusFile(Filter filter) throws SQLException
    {
        PipelineStatusFileImpl[] asf =
                Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);

        if (asf.length == 0)
            return null;

        return asf[0];
    }

    public static void setStatusFile(ViewBackgroundInfo info, PipelineStatusFileImpl sfSet, boolean notifyOnError)
            throws SQLException, Container.ContainerException
    {
        PipelineStatusFileImpl sfExist = getStatusFile(sfSet.getFilePath());
        Container c = info.getContainer();

        if (sfExist == null && c.isRoot())
        {
            throw new Container.ContainerException(
                    "Status on root container is not allowed.");
        }
        else if (sfExist != null && !c.isRoot() && !c.equals(sfExist.lookupContainer()))
        {
            throw new Container.ContainerException(
                    "Status already exists in the container '" + sfExist.getContainerPath() + "'.");
        }

        User user = info.getUser();
        if (null == sfExist)
        {
            sfSet.beforeInsert(user, c.getId());
            PipelineStatusFileImpl sfNew = Table.insert(user, pipeline.getTableInfoStatusFiles(), sfSet);

            // Make sure rowID is correct, since it might be used in email.
            sfSet.setRowId(sfNew.getRowId());
        }
        else
        {
            sfSet.beforeUpdate(user, sfExist);
            Table.update(user, pipeline.getTableInfoStatusFiles(), sfSet,
                    new Integer(sfExist.getRowId()), null);
        }

        if (notifyOnError && PipelineJob.ERROR_STATUS.equals(sfSet.getStatus()) &&
                (sfExist == null || !PipelineJob.ERROR_STATUS.equals(sfExist.getStatus())))
        {
            PipelineManager.sendNotificationEmail(sfSet, info.getContainer());
        }
    }

    public static void setStatusFile(PipelineJob job, PipelineStatusFileImpl sf)
            throws SQLException, Container.ContainerException
    {
        String status = sf.getStatus();
        String info = sf.getInfo();

        // Try to synchronize disk status first (for Perl Pipelin only)
        // If this fails, then the Perl Pipeline will not register the change
        // in status.
        try
        {
            sf.synchDiskStatus();
        }
        catch (IOException eio)
        {
            // Make sure the status changes to ERROR, to allow the user to Retry.
            status = PipelineJob.ERROR_STATUS;
            sf.setStatus(status);
            sf.setInfo("type=disk; attempting " + status + (info == null ? "" : " - " + info) + "; " +
                    eio.getMessage());
        }

        setStatusFile(job.getInfo(), sf, isNotifyOnError(job));

        if (PipelineJob.ERROR_STATUS.equals(status))
        {
            // Count this error on the job.
            job.setErrors(job.getErrors() + 1);
        }
        else if (PipelineJob.COMPLETE_STATUS.equals(status))
        {
            // Make sure the Enterprise Pipeline recognizes this as a completed
            // job, even if did it not have a TaskPipeline.
            job.setActiveTaskId(null, false);

            // Notify if this is not a split job
            if (job.getParentGUID() == null)
                PipelineManager.sendNotificationEmail(sf, job.getContainer());
        }
    }

    private static boolean isNotifyOnError(PipelineJob job)
    {
        try
        {
            return !job.isAutoRetry();
        }
        catch (Exception e)
        {
            // If we don't know, then err on the side of overnotifying.
            return true;
        }
    }

    /**
     * Update status on a status file read from the database.
     *
     * @param sf the modified status
     * @throws SQLException database error
     */
    public static void updateStatusFile(PipelineStatusFileImpl sf) throws SQLException
    {
        sf.beforeUpdate(null, sf);
        Table.update(null, pipeline.getTableInfoStatusFiles(), sf, new Integer(sf.getRowId()), null);
    }

    public static void resetJobId(String path, String jobId) throws SQLException
    {
        // If there is an existing status entry for this file, make sure it has the
        // right job GUID.
        PipelineStatusFileImpl sfExist = getStatusFile(path);
        if (sfExist != null)
        {
            sfExist.setJob(jobId);
            updateStatusFile(sfExist);
        }
    }

    public static void ensureError(PipelineJob job) throws Exception
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(job.getJobGUID());
        if (sfExist == null)
            throw new SQLException("Status for the job " + job.getJobGUID() + " was not found.");

        if (!PipelineJob.ERROR_STATUS.equals(sfExist.getStatus()))
        {
            sfExist.setStatus(PipelineJob.ERROR_STATUS);
            sfExist.setInfo(null);
            setStatusFile(job.getInfo(), sfExist, isNotifyOnError(job));
        }
    }

    public static void storeJob(String jobId, String xml) throws SQLException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            throw new SQLException("Status for the job " + jobId + " was not found.");

        StringBuffer sql = new StringBuffer();
        sql.append("UPDATE ").append(pipeline.getTableInfoStatusFiles())
                .append(" SET JobStore = ?")
                .append(" WHERE RowId = ?");

        Table.execute(pipeline.getSchema(), sql.toString(),
                new Object[] { xml, new Integer(sfExist.getRowId()) });
    }

    public static String retreiveJob(String jobId) throws SQLException
    {
        PipelineStatusFileImpl sfExist = getJobStatusFile(jobId);
        if (sfExist == null)
            throw new SQLException("Status for the job " + jobId + " was not found.");

        StringBuffer sql = new StringBuffer();
        sql.append("UPDATE ").append(pipeline.getTableInfoStatusFiles())
                .append(" SET JobStore = NULL")
                .append(" WHERE RowId = ?");

        Table.execute(pipeline.getSchema(), sql.toString(),
                new Object[] { new Integer(sfExist.getRowId()) });

        return sfExist.getJobStore();
    }

    public static PipelineStatusFileImpl[] getSplitStatusFiles(String parentId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("JobParent", parentId, CompareType.EQUAL);

        return Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    /**
     * Returns an array of <code>PipelineStatusFiles</code> for jobs not marked COMPLETE,
     * all of which were created by splitting another job.
     *
     * @param parentId the jobGUID for the joined task that created split tasks
     * @return array of <code>PipelineStatusFiles<code> not marked COMPLETE
     * @throws SQLException database error
     */
    public static PipelineStatusFileImpl[] getIncompleteStatusFiles(String parentId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
        filter.addCondition("JobParent", parentId, CompareType.EQUAL);

        return Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    public static List<PipelineStatusFileImpl> getStatusFilesForLocation(String location, boolean includeJobsOnQueue)
    {
        // NOTE: JobIds end up all uppercase in the database, but they are lowercase in jobs
        Set<String> ignoreableIds = new CaseInsensitiveHashSet();
        if (!includeJobsOnQueue)
        {
            List<PipelineJob> queuedJobs = PipelineService.get().getPipelineQueue().findJobs(location);
            for (PipelineJob job : queuedJobs)
            {
                ignoreableIds.add(job.getJobGUID());
            }
        }

        List<PipelineStatusFileImpl> result = new ArrayList<PipelineStatusFileImpl>();
        TaskPipelineRegistry registry = PipelineJobService.get();
        for (TaskFactory taskFactory : registry.getTaskFactories())
        {
            if (taskFactory.getExecutionLocation().equals(location))
            {
                TaskId id = taskFactory.getId();
                PipelineStatusFileImpl[] statusFiles = getQueuedStatusFilesForActiveTaskId(id.toString());
                for (PipelineStatusFileImpl statusFile : statusFiles)
                {
                    if (!ignoreableIds.contains(statusFile.getJobId()))
                    {
                        result.add(statusFile);
                    }
                }
            }
        }
        return result;
    }

    public static PipelineStatusFileImpl[] getQueuedStatusFilesForActiveTaskId(String activeTaskId)
    {
        try
        {
            SimpleFilter filter = createQueueFilter();
            filter.addCondition("ActiveTaskId", activeTaskId, CompareType.EQUAL);

            return Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static PipelineStatusFile[] getQueuedStatusFilesForContainer(Container c) throws SQLException
    {
        SimpleFilter filter = createQueueFilter();
        filter.addCondition("Container", c.getId(), CompareType.EQUAL);

        return Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    public static PipelineStatusFileImpl[] getQueuedStatusFiles() throws SQLException
    {
        SimpleFilter filter = createQueueFilter();
        
        return Table.select(pipeline.getTableInfoStatusFiles(), Table.ALL_COLUMNS, filter, null, PipelineStatusFileImpl.class);
    }

    private static SimpleFilter createQueueFilter()
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.ERROR_STATUS, CompareType.NEQ);
        filter.addCondition("Status", PipelineJob.SPLIT_STATUS, CompareType.NEQ);
        filter.addCondition("Job", null, CompareType.NONBLANK);
        return filter;
    }

    public static void removeStatusFile(ViewBackgroundInfo info, PipelineStatusFile sf)
            throws SQLException
    {
        String filePath = sf.getFilePath();

        StringBuffer sql = new StringBuffer();

        sql.append("DELETE FROM ").append(pipeline.getTableInfoStatusFiles())
                .append(" WHERE FilePath = ?");

        Container c = info.getContainer();
        if (c == null)
            return;

        if (c.isRoot())
        {
            Table.execute(pipeline.getSchema(), sql.toString(), new Object[] { filePath });
        }
        else
        {
            sql.append(" AND Container = ?");
            Table.execute(pipeline.getSchema(), sql.toString(), new Object[] { filePath, c.getId() });
        }
    }

    public static void completeStatus(ViewBackgroundInfo info, int[] rowIds)
            throws Exception
    {
        for (int rowId : rowIds)
        {
            PipelineStatusFileImpl sf = getStatusFile(rowId);
            if (sf == null)
                continue;
            sf.setStatus(PipelineJob.COMPLETE_STATUS);
            sf.setInfo(null);

            PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
            if (provider != null)
                provider.preCompleteStatusFile(sf);

            ViewBackgroundInfo infoSet =
                    new ViewBackgroundInfo(ContainerManager.getForId(sf.getContainerId()),
                            info.getUser(),
                            info.getUrlHelper());

            setStatusFile(infoSet, sf, false);
        }
    }

    public static void deleteStatus(ViewBackgroundInfo info, int[] rowIds)
            throws Exception
    {
        if (rowIds.length == 0)
        {
            return;
        }

        // Allow the provider for the status to do any necessary clean-up,
        // or veto the deletion.
        ArrayList<Integer> rowIdsDeleted = new ArrayList<Integer>();
        for (int rowId : rowIds)
        {
            PipelineStatusFile sf = getStatusFile(rowId);

            // First check that it still exists in the database and that it isn't running anymore
            if (sf != null && (PipelineJob.COMPLETE_STATUS.equals(sf.getStatus()) || PipelineJob.ERROR_STATUS.equals(sf.getStatus())))
            {
                PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
                if (provider != null)
                    provider.preDeleteStatusFile(sf);
                rowIdsDeleted.add(new Integer(rowId));
            }
        }

        if (rowIdsDeleted.size() > 0)
        {
            Container c = info.getContainer();
            StringBuffer sql = new StringBuffer();
            sql.append("DELETE FROM ").append(pipeline.getTableInfoStatusFiles())
                    .append(" ").append("WHERE RowId IN (SELECT RowId FROM ")
                    .append(pipeline.getTableInfoStatusFiles())
                    .append(" WHERE ");

            if (!c.isRoot())
            {
                sql.append("Container = ? AND ");
            }

            sql.append("RowId IN (");
            for (int i = 0; i < rowIdsDeleted.size() - 1; i++)
                sql.append("?,");
            sql.append("?))");

            if (c.isRoot())
            {
                Table.execute(pipeline.getSchema(), sql.toString(),
                        rowIdsDeleted.toArray(new Integer[rowIdsDeleted.size()]));
            }
            else
            {
                ArrayList<Object> params = new ArrayList<Object>();
                params.add(c.getId());
                params.addAll(rowIdsDeleted);
                Table.execute(pipeline.getSchema(), sql.toString(), params.toArray());
            }
        }
    }
}
