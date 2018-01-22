/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * The serializable data object for the current state of a pipeline job.
 *
 * @author brendanx
 */
public interface PipelineStatusFile
{
    public interface StatusReader
    {
        @Deprecated
        PipelineStatusFile getStatusFile(File logFile);
        PipelineStatusFile getStatusFile(Container container, Path logFile);

        PipelineStatusFile getStatusFile(int rowId);

        PipelineStatusFile getStatusFile(String jobGuid);

        List<? extends PipelineStatusFile> getQueuedStatusFiles() throws SQLException;

        List<? extends PipelineStatusFile> getQueuedStatusFiles(Container c) throws SQLException;

        List<? extends PipelineStatusFile> getJobsWaitingForFiles(Container c);
    }

    public interface StatusWriter
    {
        boolean setStatus(PipelineJob job, String status, @Nullable String statusInfo, boolean allowInsert) throws Exception;

        void ensureError(PipelineJob job) throws Exception;

        /**
         * If a location can be serviced by multiple servers, we record the hostname of which server is RUNNING a given task.
         * This is currently only supported for Remote Servers, but could be expanded for clusters.
         *
         * @param hostName The hostname the status writer should use when updating pipeline.StatusFiles
         */
        void setHostName(String hostName);
    }

    public interface JobStore
    {
        String toXML(PipelineJob job);

        PipelineJob fromXML(String xml);

        void storeJob(PipelineJob job) throws NoSuchJobException;

        PipelineJob getJob(String jobId);

        PipelineJob getJob(int rowId);

        void retry(String jobId) throws IOException, NoSuchJobException;

        void retry(PipelineStatusFile sf) throws IOException, NoSuchJobException;

        void split(PipelineJob job) throws IOException;

        void join(PipelineJob job) throws IOException, NoSuchJobException;
    }

    Container lookupContainer();

    boolean isActive();

    Date getCreated();

    Date getModified();

    int getRowId();

    String getJobId();

    String getJobParentId();

    /**
     * @return the name of the {@link PipelineProvider} for this job. Used to provide hooks for
     * doing work before deletion of the job, etc
     */
    @Nullable
    String getProvider();

    String getStatus();

    void setStatus(String status);

    String getInfo();

    void setInfo(String info);

    String getFilePath();

    String getDataUrl();

    String getDescription();

    String getEmail();

    boolean isHadError();

    String getJobStore();

    PipelineJob createJobInstance();

    void save();

    /**
     *
     * @return which of multiple hostnames for a location is RUNNING a task. Only set for tasks in a RUNNING state on a remote
     * server. If active task is in an inactive state or running on the web server or a cluster, this will be null.
     */
    @Nullable
    String getActiveHostName();
}

