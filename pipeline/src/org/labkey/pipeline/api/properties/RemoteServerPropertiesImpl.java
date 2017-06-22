/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.pipeline.api.properties;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJobService;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <code>RemoteServerPropertiesImpl</code> used for Spring configuration.
 */
public class RemoteServerPropertiesImpl implements PipelineJobService.RemoteServerProperties
{
    private String _location;
    private String _muleConfig;
    private String _hostName;

    public String getLocation()
    {
        return _location;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public String getMuleConfig()
    {
        return _muleConfig;
    }

    public void setMuleConfig(String muleConfig)
    {
        _muleConfig = muleConfig;
    }

    @NotNull
    public String getHostName()
    {
        if (StringUtils.isBlank(_hostName))
        {
            try
            {
                _hostName = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException e)
            {
                throw new IllegalArgumentException("Exception resolving local host for remote pipeline server properties", e);
            }
        }

        return _hostName;
    }

    public void setHostName(String hostName)
    {
         _hostName = hostName;
    }
}