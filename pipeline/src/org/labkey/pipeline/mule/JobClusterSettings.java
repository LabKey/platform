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

import org.labkey.api.pipeline.AbstractClusterSettings;
import org.labkey.api.pipeline.TaskId;

import java.util.Map;

/*
* User: jeckels
* Date: Jul 17, 2008
*/
public class JobClusterSettings extends AbstractClusterSettings
{
    private static final String PREFIX = "globus";

    private final TaskId _activeTaskId;
    private final Map<String, String> _jobParams;

    public JobClusterSettings(TaskId activeTaskId, Map<String, String> jobParams)
    {
        _activeTaskId = activeTaskId;
        _jobParams = jobParams;
    }

    private String getParam(String paramName)
    {
        return _jobParams.get(PREFIX + " " + _activeTaskId + ", " + paramName); 
    }

    private Long getLongParam(String paramName)
    {
        String value = getParam(paramName);
        return value == null ? null : new Long(value);
    }

    private Integer getIntegerParam(String paramName)
    {
        String value = getParam(paramName);
        return value == null ? null : new Integer(value);
    }

    public String getQueue()
    {
        return getParam("queue");
    }

    public Long getMaxTime()
    {
        return getLongParam("maxtime");
    }

    public Long getMaxCPUTime()
    {
        return getLongParam("maxcputime");
    }

    public Long getMaxWallTime()
    {
        return getLongParam("maxwalltime");
    }

    public Integer getHostCount()
    {
        return getIntegerParam("hostcount");
    }

    public Integer getMaxMemory()
    {
        return getIntegerParam("maxmemory");
    }
}