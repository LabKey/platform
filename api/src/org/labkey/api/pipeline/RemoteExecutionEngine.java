/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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

    /**
     * The engine should update the status for the provided jobIds, currently marked as being run/queued
     * at a location managed by this engine, by calling PipelineJob.setStatus().   */
    void updateStatusForJobs(@NotNull Collection<String> jobIds) throws PipelineJobException;

    /** Cancel a job, if possible, that is currently marked as being run/queued at a location managed by this engine */
    void cancelJob(@NotNull String jobId) throws PipelineJobException;

    @NotNull
    ConfigType getConfig();
}