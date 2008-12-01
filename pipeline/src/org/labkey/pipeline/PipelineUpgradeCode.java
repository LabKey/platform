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
package org.labkey.pipeline;

import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.NetworkDrive;
import org.labkey.pipeline.api.PipelineManager;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 1:23:48 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public class PipelineUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(PipelineUpgradeCode.class);

    // Invoked at version 8.261
    public void setPipelineToolsDirectory(ModuleContext moduleContext)
    {
        String toolsDir = StringUtils.trimToNull(AppProps.getInstance().getPipelineToolsDirectory());
        if (toolsDir == null || !NetworkDrive.exists(new File(toolsDir)))
        {
            try
            {
                WriteableAppProps props = AppProps.getWriteableInstance();
                File webappRoot = new File(ModuleLoader.getServletContext().getRealPath("/"));
                props.setPipelineToolsDir(new File(webappRoot.getParentFile(), "bin").toString());
                props.save();
            }
            catch (SQLException e)
            {
                _log.error("Failed to set pipeline tools directory.", e);
            }
        }
    }

    // Invoked at version 8.22
    public void updateRoots(ModuleContext moduleContext)
    {
        PipelineManager.updateRoots();
    }
}
