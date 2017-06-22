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

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.settings.AppProps;

/**
 * <code>ApplicationPropertiesSite</code> overrides <code>ApplicationPropertiesImpl</code>
 * with properties set in the Site Settings page.
 */
public class ApplicationPropertiesSiteSettings implements PipelineJobService.ApplicationProperties
{
    public String getToolsDirectory()
    {
        return AppProps.getInstance().getPipelineToolsDirectory();
    }

    public Character getNetworkDriveLetter()
    {
        String letter = AppProps.getInstance().getNetworkDriveLetter();
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

    public String getNetworkDrivePath()
    {
        return AppProps.getInstance().getNetworkDrivePath();
    }

    public String getNetworkDriveUser()
    {
        return AppProps.getInstance().getNetworkDriveUser();
    }

    public String getNetworkDrivePassword()
    {
        return AppProps.getInstance().getNetworkDrivePassword();
    }

    public String getBaseServerUrl()
    {
        return AppProps.getInstance().getBaseServerUrl();
    }
}
