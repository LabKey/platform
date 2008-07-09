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
package org.labkey.api.pipeline;

import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;

import java.sql.SQLException;
import java.io.IOException;

/**
 * <code>PipelineStatusFile</code>
 *
 * @author brendanx
 */
public interface PipelineStatusFile
{
    public interface StatusReader
    {
        PipelineStatusFile getStatusFile(String path) throws SQLException;
    }

    public interface StatusWriter
    {
        void setStatusFile(ViewBackgroundInfo info, PipelineJob job,
                           String status, String statusInfo) throws Exception;
    }

    public interface JobStore
    {
        String toXML(PipelineJob job);

        PipelineJob fromXML(String xml);

        void storeJob(ViewBackgroundInfo info, PipelineJob job) throws SQLException;

        PipelineJob getJob(String jobId) throws SQLException;

        void split(ViewBackgroundInfo info, PipelineJob job)
                throws IOException, SQLException;

        void join(ViewBackgroundInfo info, PipelineJob job)
                throws IOException, SQLException;
    }

    Container lookupContainer();
    
    int getRowId();

    void setRowId(int rowId);

    String getJob();

    void setJob(String job);

    String getJobParent();

    void setJobParent(String jobParent);

    String getProvider();

    void setProvider(String provider);

    String getStatus();

    void setStatus(String status);

    String getInfo();

    void setInfo(String info);

    String getFilePath();

    void setFilePath(String filePath);

    String getDataUrl();

    void setDataUrl(String dataUrl);

    String getDescription();

    void setDescription(String description);

    String getEmail();

    void setEmail(String email);

    boolean isHadError();

    void setHadError(boolean hadError);

    @Deprecated
    void synchDiskStatus() throws IOException;
}

