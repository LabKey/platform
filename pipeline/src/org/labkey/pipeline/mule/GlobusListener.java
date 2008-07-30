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

import org.globus.exec.client.GramJobListener;
import org.globus.exec.client.GramJob;
import org.globus.exec.generated.StateEnumeration;
import org.globus.exec.generated.FaultType;
import org.labkey.api.pipeline.PipelineJob;
import org.mule.umo.UMOException;
import org.oasis.wsrf.faults.BaseFaultType;
import org.oasis.wsrf.faults.BaseFaultTypeDescription;

import java.io.IOException;

public class GlobusListener implements GramJobListener
{
    private final PipelineJob _job;

    public GlobusListener(PipelineJob job)
    {
        _job = job;
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
                    logFault(fault, _job);
                }
                PipelineJobRunnerGlobus.updateStatus(_job, newStatus);
            }
            catch (UMOException e)
            {
                _job.error("Failed up update job status to " + newStatus, e);
            }
            catch (IOException e)
            {
                _job.error("Failed up update job status to " + newStatus, e);
            }
        }
    }

    private void logFault(BaseFaultType fault, PipelineJob job)
    {
        if (fault instanceof FaultType)
        {
            job.error("Fault received from Globus on \"" + ((FaultType)fault).getCommand() + "\" command");
        }
        else
        {
            job.error("Fault received from Globus");
        }
        if (fault.getDescription() != null)
        {
            for (BaseFaultTypeDescription description : fault.getDescription())
            {
                job.error(description.get_value());
            }
        }
        if (fault.getFaultCause() != null)
        {
            for (BaseFaultType cause : fault.getFaultCause())
            {
                logFault(cause, job);
            }
        }
    }

}