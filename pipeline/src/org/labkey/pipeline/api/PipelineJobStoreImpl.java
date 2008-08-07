/*
 * Copyright (c) 2008 LabKey Corporation
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

import java.sql.SQLException;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.labkey.api.pipeline.*;
import org.labkey.api.data.DbScope;
import com.thoughtworks.xstream.converters.ConversionException;

/**
 * Implements serialization of a <code>PipelineJob</code> to and from XML,
 * and storage and retrieval from the <code>PipelineJobStatusManager</code>. 
 */
public class PipelineJobStoreImpl extends PipelineJobMarshaller
{
    public PipelineJob fromXML(String xml)
    {
        PipelineJob job = super.fromXML(xml);
        job.restoreQueue(PipelineService.get().getPipelineQueue());
        return job;
    }

    public PipelineJob getJob(String jobId) throws SQLException
    {
        return fromStatus(PipelineStatusManager.retreiveJob(jobId));
    }

    public void retry(String jobId) throws IOException, SQLException
    {
        retry(PipelineStatusManager.getJobStatusFile(jobId));
    }

    public void retry(PipelineStatusFile sf) throws IOException
    {
        try
        {
            PipelineJob job = fromStatus(sf.getJobStore());
            if (job == null)
                throw new IOException("Job checkpoint does not exist.");

            // If the job is being retried from a non-error status, then don't
            // increment error and retry counts.  This happens when a server restart
            // causes all previously queued jobs to be requeued.
            if (PipelineJob.ERROR_STATUS.equals(sf.getStatus()))
                job.retryUpdate();

            PipelineService.get().getPipelineQueue().addJob(job);
        }
        catch (ConversionException e)
        {
            throw new IOException("Failed to restore the checkpoint from the database.");
        }
    }

    private PipelineJob fromStatus(String xml)
    {
        if (xml == null || xml.length() == 0)
            return null;
        
        PipelineJob job = fromXML(xml);

        // If it was stored, then it can't be on a queue.
        job.clearQueue();
        
        return job;
    }

    public void storeJob(PipelineJob job) throws SQLException
    {
        PipelineStatusManager.storeJob(job.getJobGUID(), toXML(job));
    }

    private ThreadLocal<SplitRecord> _splitRecord = new ThreadLocal<SplitRecord>();

    public void split(PipelineJob job) throws IOException, SQLException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try
        {
            beginTransaction(scope, active);

            PipelineJob[] jobs = job.createSplitJobs();

            beginSplit(job, jobs);

            // Queue all the split jobs.
            for (PipelineJob jobSplit : jobs)
                PipelineService.get().queueJob(jobSplit);

            // If there were any split jobs left incomplete, then store the job, and
            // wait for them to complete.
            if (getIncompleteSplitCount(job.getJobGUID()) > 0)
            {
                storeJob(job);
                job.setStatus(PipelineJob.SPLIT_STATUS);
            }
            commitTransaction(scope, active);
        }
        finally
        {
            endSplit();
            closeTransaction(scope, active);
        }
    }

    public void join(PipelineJob job) throws IOException, SQLException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        boolean active = scope.isTransactionActive();
        try
        {
            TaskId tid = job.getActiveTaskId();

            beginTransaction(scope, active);
            
            // Avoid deadlock by doing this select first.
            int count = getIncompleteSplitCount(job.getParentGUID());

            setCompleteSplit(job);

            PipelineJob jobJoin = getJob(job.getParentGUID());
            jobJoin.mergeSplitJob(job);
            if (count == 1)
            {
                // All split jobs have completed; begin running the joined job again
                jobJoin.setActiveTaskId(tid, false);
                PipelineService.get().queueJob(jobJoin);
            }
            else
            {
                // More split jobs left; store the join job until they complete
                storeJob(jobJoin);
            }
            commitTransaction(scope, active);
        }
        finally
        {
            closeTransaction(scope, active);
        }
    }

    private void beginTransaction(DbScope scope, boolean active) throws SQLException
    {
        if (!active)
            scope.beginTransaction();
    }

    private void commitTransaction(DbScope scope, boolean active) throws SQLException
    {
        if (!active)
            scope.commitTransaction();
    }

    private void closeTransaction(DbScope scope, boolean active) throws SQLException
    {
        if (!active && scope.isTransactionActive())
            scope.rollbackTransaction();
    }

    private static class SplitRecord
    {
        private PipelineJob _job;
        private List<PipelineJob> _splitJobs;

        public SplitRecord(PipelineJob job, PipelineJob[] splitJobs)
        {
            _job = job;
            // Need list with editable content.
            _splitJobs = new ArrayList<PipelineJob>(Arrays.asList(splitJobs));
        }

        public boolean isJoinJob(String jobId)
        {
            return jobId.equals(_job.getJobGUID());
        }

        public int getIncompleteCount()
        {
            return _splitJobs.size();
        }

        public boolean complete(PipelineJob job)
        {
            return _splitJobs.remove(job);
        }
    }

    private void beginSplit(PipelineJob job, PipelineJob[] splitJobs)
    {
        _splitRecord.set(new SplitRecord(job, splitJobs));
    }

    private void endSplit()
    {
        _splitRecord.set(null);
    }

    private int getIncompleteSplitCount(String parentJobId) throws SQLException
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(parentJobId))
            return rec.getIncompleteCount();

        // Check the database.
        return PipelineStatusManager.getIncompleteStatusFiles(parentJobId).length;
    }

    private void setCompleteSplit(PipelineJob job)
    {
        SplitRecord rec = _splitRecord.get();
        if (rec != null && rec.isJoinJob(job.getParentGUID()))
            rec.complete(job);

        job.setStatus(PipelineJob.COMPLETE_STATUS);
    }
}
