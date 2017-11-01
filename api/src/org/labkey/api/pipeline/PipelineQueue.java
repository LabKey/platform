/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

/**
 * PipelineQueues accept submissions of jobs and some of their basic management. Implementations might run all work in
 * a single location, such as the web server, or know how to hand jobs off to remote computing resources.
 */
public interface PipelineQueue
{
    /**
     * Add a <code>PipelineJob</code> to this queue to be run.
     *
     * @param job Job to be run
     *
     */
    void addJob(PipelineJob job) throws PipelineValidationException;

    /**
     * Cancel a previously added <code>PipelineJob</code>.  The job may be still on the
     * queue or running.
     *
     * @param user the user issuing the cancel request
     * @param c Filter for jobs started from this container.  Use null for any job.
     * @param statusFile The status object for the job
     * @return True if the job was successfully cancelled
     */
    boolean cancelJob(User user, Container c, PipelineStatusFile statusFile);

    /**
     * This method is used to restore lost jobs, when the LabKey Server or a remote
     * JMS listener restarts.  The known contents of the queue are inspected against
     * jobs in the StatusFiles table.
     * 
     * @param location The location by which to filter the jobs
     * @return A list of jobs currently know to the queue
     */
    List<PipelineJob> findJobs(String location);

    /**
     * @return True if this queue is running in the LabKey Server VM.
     */
    boolean isLocal();

    /**
     * @return True if this queue has no durable storage.
     */
    boolean isTransient();

    /**
     * Pipeline queue maintenance life-cycle event which is only called by the mini-
     * pipeline.  Avoid putting important functionality in the implementation
     * for this method.  It is intended to enable queue management in the
     * mini-pipeline.
     *
     * @param job The pipeline job that is starting
     * @param thread The thread on which it will run
     */
    void starting(PipelineJob job, Thread thread);

    /**
     * Pipeline queue maintenance life-cycle event which is only called by the mini-
     * pipeline.  Avoid putting important functionality in the implementation
     * for this method.  It is intended to enable queue management in the
     * mini-pipeline.
     *
     * @param job The pipeline job that is ending
     */
    void done(PipelineJob job);

    /**
     * Attempt to find a job running in memory on the LabKey Server with the
     * specified status file path.  This only works on the mini-pipeline, and
     * is currently only used in a single case to show enriched information
     * about the running job's state for the flow <code>JobStatusView</code>.
     * <p/>
     * Avoid adding any new dependencies on this code, and especially anything
     * that does not degrade gracefully, when no in memory job is returned for
     * something the pipeline status indicates is running.  It may be running
     * on another machine.
     *
     * @param c The container in which the job was started
     * @param statusFile The file path associated with this job for its status
     * @return An job actively running in memory on this server
     */
    @Deprecated
    PipelineJob findJobInMemory(Container c, String statusFile);

    /**
     * An old way for getting data about running jobs that assumes they are all
     * in memory on the LabKey Server.  This is still supported for some backward
     * compatibility, but in the EnterprisePipeline it is not very useful, since
     * if just reads from the JMS queue, and a lot may now be pulled from that
     * queue longe before it is completed.
     *
     * @param c Filter for jobs started in this container. Use null for all jobs.
     * @return Jobs on the queue separated into running and pending lists
     */
    @Deprecated
    PipelineJobData getJobDataInMemory(Container c);
}
