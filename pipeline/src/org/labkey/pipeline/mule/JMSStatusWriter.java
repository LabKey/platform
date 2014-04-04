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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineJob;
import org.mule.extras.client.MuleClient;
import org.mule.impl.RequestContext;
import org.mule.umo.UMOEvent;

/**
 * Writes status information to the JMS queue to be processed by some other object, most likely on a different machine
 * User: jeckels
 * Date: Aug 27, 2008
 */
public class JMSStatusWriter implements PipelineStatusFile.StatusWriter
{
    public static final String STATUS_QUEUE_NAME = "StatusQueue";
    private String hostName;

    @Override
    public void setHostName(@NotNull String hostName)
    {
        this.hostName = hostName;
    }

    public boolean setStatus(PipelineJob job, String status, String statusInfo, boolean allowInsert) throws Exception
    {
        if (job.getActiveTaskStatus() == PipelineJob.TaskStatus.complete)
        {
            // Don't bother setting the status, since the job will be immediately going onto the standard job queue
            // to determine if there's another task.
            return true;
        }

        final StatusChangeRequest s = new StatusChangeRequest(job, status, statusInfo, hostName);

        // Mule uses ThreadLocals to store the current event. Writing this status to the JMS queue will replace the
        // event, so we need to grab the current one so that we can restore it after queuing up the new status
        UMOEvent currentEvent = RequestContext.getEvent();
        MuleClient client = null;
        try
        {
            client = new MuleClient();
            client.dispatch(STATUS_QUEUE_NAME, s, null);
        }
        finally
        {
            if (client != null)
            {
                try { client.dispose(); } catch (Exception e) {}
                if (currentEvent == null)
                {
                    RequestContext.clear();
                }
                else
                {
                    // Restore the event that we're processing
                    RequestContext.setEvent(currentEvent);
                }
            }
        }
        return true;
    }

    public void ensureError(PipelineJob job) throws Exception
    {
        throw new UnsupportedOperationException("Method supported only on web server");
    }
}