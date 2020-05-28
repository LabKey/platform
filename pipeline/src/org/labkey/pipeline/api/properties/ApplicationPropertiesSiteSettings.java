/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.NetworkDriveProps;

/**
 * <code>ApplicationPropertiesSite</code> overrides <code>ApplicationPropertiesImpl</code>
 * with properties set in the Site Settings page.
 */
public class ApplicationPropertiesSiteSettings implements PipelineJobService.ApplicationProperties
{
    @Override
    public String getToolsDirectory()
    {
        return AppProps.getInstance().getPipelineToolsDirectory();
    }

    @Override
    public Character getNetworkDriveLetter()
    {
        String letter = NetworkDriveProps.getNetworkDriveLetter();
        if (letter == null || letter.length() == 0)
        {
            return null;
        }
        if (letter.length() > 1)
        {
            throw new IllegalStateException("Network drive letter should be exactly one character long but is '" + letter + "'");
        }
        return letter.charAt(0);
    }

    @Override
    public String getNetworkDrivePath()
    {
        return NetworkDriveProps.getNetworkDrivePath();
    }

    @Override
    public String getNetworkDriveUser()
    {
        return NetworkDriveProps.getNetworkDriveUser();
    }

    @Override
    public String getNetworkDrivePassword()
    {
        return NetworkDriveProps.getNetworkDrivePassword();
    }

    public String getBaseServerUrl()
    {
        return AppProps.getInstance().getBaseServerUrl();
    }
}
