/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineJob;

/**
 * Used when running a job through a remote execution engine. The cluster itself may handle giving the web
 * server job status updates through a callback so we don't need to duplicate the status info.
 * User: jeckels
 * Date: Jul 18, 2008
*/
public class NoOpPipelineStatusWriter implements PipelineStatusFile.StatusWriter
{
    @Override
    public boolean setStatus(PipelineJob job, String status, String statusInfo, boolean allowInsert)
    {
        return true;
    }

    @Override
    public void ensureError(PipelineJob job)
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }

    @Override
    public void setHostName(String hostName)
    {
        throw new UnsupportedOperationException("Method supported only on remote servers");
    }
}