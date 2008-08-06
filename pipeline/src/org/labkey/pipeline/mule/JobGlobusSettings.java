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

import org.labkey.api.pipeline.AbstractGlobusSettings;

import java.util.Map;

/*
* User: jeckels
* Date: Jul 17, 2008
*/
public class JobGlobusSettings extends AbstractGlobusSettings
{
    private final String _groupParameterName;
    private final Map<String, String> _jobParams;

    public JobGlobusSettings(String groupParameterName, Map<String, String> jobParams)
    {
        _groupParameterName = groupParameterName;
        _jobParams = jobParams;
    }

    private String getParam(String paramName)
    {
        return _jobParams.get(_groupParameterName + ", globus " + paramName);
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
        return getLongParam("max time");
    }

    public Long getMaxCPUTime()
    {
        return getLongParam("max cpu-time");
    }

    public Long getMaxWallTime()
    {
        return getLongParam("max wall-time");
    }

    public Integer getHostCount()
    {
        return getIntegerParam("host count");
    }

    public Integer getMaxMemory()
    {
        return getIntegerParam("max memory");
    }
}