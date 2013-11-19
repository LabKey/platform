/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.globus.exec.client.GramJobListener;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.StateEnumeration;
import org.globus.exec.generated.FaultType;
import org.globus.wsrf.NotificationConsumerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.mule.umo.UMOException;
import org.oasis.wsrf.faults.BaseFaultType;
import org.oasis.wsrf.faults.BaseFaultTypeDescription;
import org.oasis.wsrf.lifetime.ResourceNotDestroyedFaultType;

import java.io.IOException;

public class GlobusListener implements GramJobListener
{
    private final PipelineJob _job;
    private final NotificationConsumerManager _notifConsumerManager;

    public GlobusListener(PipelineJob job, NotificationConsumerManager notifConsumerManager)
    {
        _job = job;
        _notifConsumerManager = notifConsumerManager;
    }

    public void stateChanged(GramJob gramJob)
    {
        PipelineJob.TaskStatus newStatus = null;
        if (gramJob.getState() == StateEnumeration.Done)
        {
            if (gramJob.getExitCode() != 0)
            {
                newStatus = PipelineJob.TaskStatus.error;
                _job.error("Job completed with exit code " + gramJob.getExitCode());
            }
            else
            {
                newStatus = PipelineJob.TaskStatus.complete;
            }
        }
        else if (gramJob.getState() == StateEnumeration.Failed)
        {
            newStatus = PipelineJob.TaskStatus.error;
        }
        else if (gramJob.getState() == StateEnumeration.Active)
        {
            newStatus = PipelineJob.TaskStatus.running;
        }

        if (newStatus != null)
        {
            try
            {
                FaultType fault = gramJob.getFault();
                if (fault != null)
                {
                    boolean cancelled = logFault(fault, _job, true);
                    if (cancelled && newStatus == PipelineJob.TaskStatus.error)
                    {
                        newStatus = PipelineJob.TaskStatus.cancelled;
                    }
                }
                else if (gramJob.getState() == StateEnumeration.Failed)
                {
                    _job.error("Received error callback from Globus, but no information on the cause of the fault");
                }
                PipelineJobRunnerGlobus.updateStatus(_job, newStatus);
            }
            catch (UMOException | IOException e)
            {
                _job.error("Failed up update job status to " + newStatus, e);
            }

            if (!newStatus.isActive())
            {
                // Attempt to clean-up Globus               
                try
                {
                    try
                    {
                        gramJob.unbind();
                    }
                    catch (NullPointerException e)
                    {
                        // This is a very ugly hack, a workaround for a partial implementation inside of GramJob.
                        // GramJob supports setting the NotificationConsumerEPR, but it doesn't clean up correctly
                        // since it doesn't have a reference to the NotificationConsumerManager. At the end of the
                        // unbind() method it NPEs because it doesn't have a reference. So, we catch that NPE
                        // and finish the cleanup ourselves.
                        _notifConsumerManager.removeNotificationConsumer(gramJob.getNotificationConsumerEPR());
                        _notifConsumerManager.stopListening();
                    }
                    catch (ResourceNotDestroyedFaultType e)
                    {
                        if (newStatus != PipelineJob.TaskStatus.cancelled)
                        {
                            _job.warn("Failed to unbind GRAM job");
                        }
                    }
                    catch (Throwable e)
                    {
                        _job.warn("Exception trying to unbind GRAM job", e);
                    }
                    gramJob.removeListener(this);
                    gramJob.destroy();
                }
                catch (Throwable e)
                {
                    _job.warn("Exception trying to clean up GRAM", e);
                }
            }
        }
    }

    /** @return true if the fault indicates that job had been cancelled */
    private boolean logFault(BaseFaultType fault, PipelineJob job, boolean parentFault)
    {
        boolean cancelled = false;
        boolean loggedFault = false;
        if (fault instanceof FaultType && ((FaultType)fault).getCommand() != null)
        {
            job.info("Fault received from Globus on \"" + ((FaultType)fault).getCommand() + "\" command");
            loggedFault = true;
        }
        if (fault.getDescription() != null)
        {
            for (BaseFaultTypeDescription description : fault.getDescription())
            {
                String message = description.get_value();
                if (!FaultType.class.getName().equals(message))
                {
                    if (message != null && message.contains("canceled by the user"))
                    {
                        cancelled = true;
                    }
                    job.info(description.get_value());
                }
            }
        }
        if (parentFault && !cancelled && !loggedFault)
        {
            job.info("Fault received from Globus");
        }
        if (fault.getFaultCause() != null)
        {
            for (BaseFaultType cause : fault.getFaultCause())
            {
                cancelled |= logFault(cause, job, false);
            }
        }
        return cancelled;
    }

}