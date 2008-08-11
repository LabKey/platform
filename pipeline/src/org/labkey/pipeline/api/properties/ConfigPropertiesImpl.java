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

import java.util.Map;

/**
 * <code>ConfigPropertiesImpl</code> used for Spring configuration.
 */
public class ConfigPropertiesImpl implements PipelineJobService.ConfigProperties
{
    private Map<String, String> _softwarePackages;

    public String getSoftwarePackagePath(String packageName)
    {
        if (packageName == null || _softwarePackages == null)
            return null;

        return _softwarePackages.get(packageName);
    }

    public Map<String, String> getSoftwarePackages()
    {
        return _softwarePackages;
    }

    public void setSoftwarePackages(Map<String, String> softwarePackages)
    {
        _softwarePackages = softwarePackages;
    }
}