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

    public void split(PipelineJob job) throws IOException, SQLException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        try
        {
            scope.beginTransaction();
            for (PipelineJob jobSplit : job.createSplitJobs())
                PipelineService.get().queueJob(jobSplit);
            storeJob(job);
            job.setStatus(PipelineJob.SPLIT_STATUS);
            scope.commitTransaction();
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }

    public void join(PipelineJob job) throws IOException, SQLException
    {
        DbScope scope = PipelineSchema.getInstance().getSchema().getScope();
        try
        {
            TaskId tid = job.getActiveTaskId();

            scope.beginTransaction();
            // Avoid deadlock by doing this select first.
            int count = PipelineStatusManager.getIncompleteStatusFiles(job.getParentGUID()).length;

            job.setStatus(PipelineJob.COMPLETE_STATUS);

            PipelineJob jobJoin = getJob(job.getParentGUID());
            jobJoin.mergeSplitJob(job);
            if (count == 1)
            {
                // All split jobs have completed; begin running the joined job again
                jobJoin.setActiveTaskId(tid);
                PipelineService.get().queueJob(jobJoin);
            }
            else
            {
                // More split jobs left; store the join job until they complete
                storeJob(jobJoin);
            }
            scope.commitTransaction();
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }
}
