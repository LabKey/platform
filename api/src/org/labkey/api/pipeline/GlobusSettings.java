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

/**
* Configuration for submitting to a cluster through Globus. See http://www.globus.org/toolkit/docs/4.0/execution/wsgram/schemas/gram_job_description.html.
* User: jeckels
* Date: Jul 16, 2008
*/
public interface GlobusSettings
{
    /** @return the name of the scheduler queue to which the job should be submitted */
    public String getQueue();

    /**
     * @return The maximum walltime or cputime per executable process.
     * Walltime or cputime is selected by the GRAM scheduler being interfaced. The units is in minutes.
     */
    public Long getMaxTime();

    /**
     * @return Explicitly set the maximum cputime per executable process. The units is in minutes.
     */
    public Long getMaxCPUTime();

    /**
     * @return Explicitly set the maximum walltime per executable process. The units is in minutes.
     */
    public Long getMaxWallTime();

    /**
     * @return Explicitly set the maximum amount of memory per executable process. The units is in Megabytes.
     */
    public Integer getMaxMemory();

    /**
     * @return Explicitly set the termination time, the time at which Globus will kill the job. The units is in minutes,
     * relative to when the Globus job is submitted, not when it runs.
     */
    public Integer getTerminationTime();

    /**
     * Takes all the non-null values from the overrides object and uses them to replace the values on this object.
     * @param overrides the set of properties to override
     * @return a new ClusterSettings object with the merged properties
     */
    public GlobusSettings mergeOverrides(GlobusSettings overrides);
}