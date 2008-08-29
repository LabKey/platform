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
package org.labkey.pipeline.mule;

import org.mule.impl.DefaultComponentExceptionStrategy;
import org.mule.impl.RequestContext;
import org.mule.umo.UMOEvent;
import org.mule.umo.ComponentException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;

/*
* User: jeckels
* Date: Jul 22, 2008
*/
public class PipelineJobExceptionStrategy extends DefaultComponentExceptionStrategy
{
    protected void defaultHandler(Throwable t)
    {
        super.defaultHandler(t);
        UMOEvent event = RequestContext.getEvent();
        if (event != null && event.getMessage() != null)
        {
            Object payload = event.getMessage().getPayload();
            if (payload instanceof PipelineJob)
            {
                handleJobError(t, (PipelineJob)payload);
            }
            else
            {
                // Might be able to deserialize XML to reconstruct the job so that we can put it into the error state
                try
                {
                    String message = event.getMessageAsString();
                    PipelineJob job = PipelineJobService.get().getJobStore().fromXML(message);
                    handleJobError(t, job);
                }
                catch (Exception e)
                {
                    // Couldn't deserialize from XML, must not have been a job
                }
            }
        }
    }

    private void handleJobError(Throwable t, PipelineJob job)
    {
        String msg;
        if (t instanceof ComponentException && t.getCause() != null)
        {
            msg = t.getCause().getMessage();
        }
        else
        {
            msg = t.getMessage();
        }
        if (msg == null)
            msg = t.getClass().toString();
        job.error(msg, t);
    }
}