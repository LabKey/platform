/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.CancelledException;
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
    private static final Logger LOGGER = Logger.getLogger(PipelineJobExceptionStrategy.class);

    protected void defaultHandler(Throwable t)
    {
        super.defaultHandler(t);
        UMOEvent event = RequestContext.getEvent();
        if (event != null && event.getMessage() != null)
        {
            Object payload = event.getMessage().getPayload();
            if (payload instanceof PipelineJob)
            {
                try
                {
                    handleJobError(t, (PipelineJob)payload);
                }
                catch (RuntimeException e)
                {
                    // Don't let this propagate up the stack or it can kill the Mule thread
                    LOGGER.error("Failed to fully handle setting error", e);
                }
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
        if (t instanceof ComponentException && t.getCause() != null)
        {
            t = t.getCause();
        }

        // CancelledExceptions can make it here, since that's required to prevent the job from moving to its
        // next task. We don't want to log them, or set the job to ERROR when we see them
        if (!(t instanceof CancelledException))
        {
            String msg = t.getMessage();
            if (msg == null)
                msg = t.getClass().getName();
            job.error(msg, t);
        }
    }
}