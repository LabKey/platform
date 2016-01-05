package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * RemoteExecutionEngine implementations know how to submit jobs to some sort of external execution engine, such as a
 * cluster. They are responsible for managing communication with the remote resource.
 *
 * Implementations are also responsible for monitoring status changes and updating the job's record
 * via PipelineJobService.get().getStatusWriter().setStatus(). The exception is that the core server, at restart time,
 * will requery all jobs that were active or queued.
 *
 * Created by: jeckels
 * Date: 11/16/15
 */
public interface RemoteExecutionEngine<ConfigType extends PipelineJobService.RemoteExecutionEngineConfig>
{
    /** @return a unique name for this type of execution engine, such as 'HTCondor' */
    @NotNull
    String getType();

    /** Submit a job for execution */
    void submitJob(@NotNull PipelineJob job) throws PipelineJobException;

    /** @return the status of the requested job, currently marked as being run/queued at a location managed by this engine */
    String getStatus(@NotNull String jobId) throws PipelineJobException;

    /** Cancel a job, if possible, that is currently marked as being run/queued at a location managed by this engine */
    void cancelJob(@NotNull String jobId) throws PipelineJobException;

    ConfigType getConfig();
}