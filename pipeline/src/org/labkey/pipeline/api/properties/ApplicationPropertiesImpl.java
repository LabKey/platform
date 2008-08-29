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

package org.labkey.pipeline.api.properties;

import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;

/**
 * User: jeckels
 * Date: Apr 29, 2008
 */
public class ApplicationPropertiesImpl implements PipelineJobService.ApplicationProperties
{
    private String _toolsDirectory;

    private Character _networkDriveLetter;
    private String _networkDrivePath;
    private String _networkDriveUser;
    private String _networkDrivePassword;

    public String getToolsDirectory()
    {
        return _toolsDirectory;
    }

    public void setToolsDirectory(String toolsDirectory)
    {
        if (!new File(toolsDirectory).isDirectory())
            throw new IllegalArgumentException("Tools directory " + toolsDirectory + " is not a directory.");

        _toolsDirectory = toolsDirectory;
    }

    public Character getNetworkDriveLetter()
    {
        return _networkDriveLetter;
    }

    public void setNetworkDriveLetter(Character networkDriveLetter)
    {
        _networkDriveLetter = networkDriveLetter;
    }

    public String getNetworkDrivePath()
    {
        return _networkDrivePath;
    }

    public void setNetworkDrivePath(String networkDrivePath)
    {
        _networkDrivePath = networkDrivePath;
    }

    public String getNetworkDriveUser()
    {
        return _networkDriveUser;
    }

    public void setNetworkDriveUser(String networkDriveUser)
    {
        _networkDriveUser = networkDriveUser;
    }

    public String getNetworkDrivePassword()
    {
        return _networkDrivePassword;
    }

    public void setNetworkDrivePassword(String networkDrivePassword)
    {
        _networkDrivePassword = networkDrivePassword;
    }
}
