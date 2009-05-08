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
* Date: Jul 17, 2008
*/
public abstract class AbstractGlobusSettings implements GlobusSettings
{
    public GlobusSettings mergeOverrides(GlobusSettings overrides)
    {
        GlobusSettingsImpl result = new GlobusSettingsImpl();
        if (overrides == null)
        {
            overrides = new GlobusSettingsImpl();
        }
        result.setMaxCPUTime(overrides.getMaxCPUTime() == null ? getMaxCPUTime() : overrides.getMaxCPUTime());
        result.setMaxMemory(overrides.getMaxMemory() == null ? getMaxMemory() : overrides.getMaxMemory());
        result.setMaxTime(overrides.getMaxTime() == null ? getMaxTime() : overrides.getMaxTime());
        result.setMaxWallTime(overrides.getMaxWallTime() == null ? getMaxWallTime() : overrides.getMaxWallTime());
        result.setQueue(overrides.getQueue() == null ? getQueue() : overrides.getQueue());
        result.setTerminationTime(overrides.getTerminationTime() == null ? getTerminationTime() : overrides.getTerminationTime());
        return result;
    }
}