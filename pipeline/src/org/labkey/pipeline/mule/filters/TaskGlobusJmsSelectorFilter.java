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

import java.util.List;

/**
 * <code>TaskJmsSelectorFilter</code> builds and applies a JMS selector for
 * all registered <code>TaskFactory</code> objects for all configured Globus locations.
 *
 * @author brendanx
 */
public class TaskGlobusJmsSelectorFilter extends TaskJmsSelectorFilter
{
    public static final String DEFALUT_GLOBUS_LOCATION = "cluster";

    public TaskGlobusJmsSelectorFilter()
    {
        List<PipelineJobService.GlobusClientProperties> allGlobuses = PipelineJobService.get().getGlobusClientPropertiesList();
        _locations.add(DEFALUT_GLOBUS_LOCATION);

        boolean foundDefaultGlobusLocation = false;

        for (PipelineJobService.GlobusClientProperties globusSettings : allGlobuses)
        {
            if (globusSettings.getLocation() == null || globusSettings.getLocation().equalsIgnoreCase(DEFALUT_GLOBUS_LOCATION))
            {
                if (foundDefaultGlobusLocation)
                {
                    throw new IllegalStateException("A maximum of one Globus configuration is allowed to use the default cluster location");
                }
                foundDefaultGlobusLocation = true;
            }
            else
            {
                if (_locations.contains(globusSettings.getLocation()))
                {
                    throw new IllegalStateException("Globus location names must be unique, but two were found for location '" + globusSettings.getLocation() + "'");
                }
                _locations.add(globusSettings.getLocation());
            }
        }
    }
}