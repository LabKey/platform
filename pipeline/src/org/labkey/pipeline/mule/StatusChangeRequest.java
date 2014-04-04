/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.pipeline.mule;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

import java.io.Serializable;
import java.sql.SQLException;

/**
 * Transfer object for remote status changes.
 */
public class StatusChangeRequest implements Serializable, StatusRequest
{
    private String _status;
    private String _statusInfo;
    private String _jobId;
    private TaskId _activeTaskId;
    private String _hostName;

    public StatusChangeRequest(PipelineJob job, String status, String statusInfo, String hostName)
    {
        _status = status;
        _statusInfo = statusInfo;
        _jobId = job.getJobGUID();
        _activeTaskId = job.getActiveTaskId();
        _hostName = hostName;
    }

    public void performRequest() throws SQLException
    {
        PipelineStatusFileImpl file = PipelineStatusManager.getJobStatusFile(_jobId);
        // Make sure that the job hasn't moved on to another task already
        if (file != null && _activeTaskId.toString().equals(file.getActiveTaskId()))
        {
            file.setStatus(_status);
            file.setInfo(_statusInfo);
            file.setActiveHostName(_hostName);
            file.beforeUpdate(null, file);
            PipelineStatusManager.updateStatusFile(file);
        }
    }
}
