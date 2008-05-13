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

package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;

/**
 * User: jeckels
 * Date: Apr 29, 2008
 */
public class SpringApplicationProperties implements PipelineJobService.ApplicationProperties
{
    private String _toolsDirectory;

    public String getToolsDirectory()
    {
        return _toolsDirectory;
    }

    public void setToolsDirectory(String toolsDirectory)
    {
        _toolsDirectory = toolsDirectory;
    }
}
