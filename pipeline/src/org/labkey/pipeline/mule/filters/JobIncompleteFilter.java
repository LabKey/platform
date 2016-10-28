/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.pipeline.mule.filters;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.mule.EPipelineQueueImpl;
import org.mule.umo.UMOFilter;
import org.mule.umo.UMOMessage;

/**
 * <code>JobIncompleteFilter</code>
 *
 * @author brendanx
 */
public class JobIncompleteFilter implements UMOFilter
{
    public boolean accept(UMOMessage message)
    {
        Object payload = message.getPayload();

        assert payload instanceof PipelineJob
                : "Invalid type " + payload.getClass() + " for JobIncompleteFilter.";

        if (EPipelineQueueImpl.getOutboundJobs() != null)
            return true;

        PipelineJob job = (PipelineJob) payload;
        switch (job.getActiveTaskStatus())
        {
            case error:
                return false;

            case complete:
                return (job.getActiveTaskId() != null);

            default:
                return true;
        }
    }
}
