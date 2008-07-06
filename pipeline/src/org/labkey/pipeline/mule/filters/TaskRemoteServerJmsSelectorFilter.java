/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
package org.labkey.pipeline.mule.filters;

import org.labkey.api.pipeline.PipelineJobService;

/**
 * <code>TaskJmsSelectorFilter</code> builds and applies a JMS selector for
 * all registered <code>TaskFactory</code> objects for a specified location.
 *
 * @author brendanx
 */
public class TaskRemoteServerJmsSelectorFilter extends AbstractTaskJmsSelectorFilter
{
    private String _location;

    public TaskRemoteServerJmsSelectorFilter()
    {
        PipelineJobService.RemoteServerProperties props =
                PipelineJobService.get().getRemoteServerProperties();
        if (props != null)
            _location = props.getLocation();

        if (_location == null || "".equals(_location))
            throw new IllegalArgumentException("RemoteServerProperties.location must be set to use TaskRemoteServerJmsSelectorFilter.");            
    }

    public String getLocation()
    {
        return _location;
    }
}