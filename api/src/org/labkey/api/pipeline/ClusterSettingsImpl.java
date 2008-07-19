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

/*
* User: jeckels
* Date: Jul 16, 2008
*/
public class ClusterSettingsImpl extends AbstractClusterSettings
{
    private Integer _maxMemory;
    private String _queue;
    private Long _maxTime;
    private Long _maxCPUTime;
    private Long _maxWallTime;
    private Integer _hostCount;

    public String getQueue()
    {
        return _queue;
    }

    public Long getMaxTime()
    {
        return _maxTime;
    }

    public Long getMaxCPUTime()
    {
        return _maxCPUTime;
    }

    public Long getMaxWallTime()
    {
        return _maxWallTime;
    }

    public Integer getHostCount()
    {
        return _hostCount;
    }

    public Integer getMaxMemory()
    {
        return _maxMemory;
    }

    public void setMaxMemory(Integer maxMemory)
    {
        _maxMemory = maxMemory;
    }

    public void setQueue(String queue)
    {
        _queue = queue;
    }

    public void setMaxTime(Long maxTime)
    {
        _maxTime = maxTime;
    }

    public void setMaxCPUTime(Long maxCPUTime)
    {
        _maxCPUTime = maxCPUTime;
    }

    public void setMaxWallTime(Long maxWallTime)
    {
        _maxWallTime = maxWallTime;
    }

    public void setHostCount(Integer hostCount)
    {
        _hostCount = hostCount;
    }
}