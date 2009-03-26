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
package org.labkey.query;

import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.security.UserManager;

import java.util.Map;

/*
* User: Karl Lum
* Date: Dec 10, 2008
* Time: 9:51:32 AM
*/
public class QueryUpgradeCode implements UpgradeCode
{
    private static final String R_EXE = "RReport.RExe";
    private static final String R_CMD = "RReport.RCmd";
    private static final String R_TEMP_FOLDER = "RReport.TempFolder";
    private static final String R_EDIT_PERMISSIONS = "RReport.EditPermissions";

    // Invoked at version 9.1
    public void upgradeRConfiguration(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            Map<String, String> map = UserManager.getUserPreferences(false);
            if (map != null && map.containsKey(R_EXE))
            {
                ExternalScriptEngineDefinition def = LabkeyScriptEngineManager.createDefinition();

                def.setName("R Scripting Engine");
                def.setExeCommand(map.get(R_CMD));
                def.setExePath(map.get(R_EXE));
                def.setExtensions(new String[]{"R", "r"});
                def.setLanguageName("R");
                def.setOutputFileName("script.Rout");
                def.setEnabled(true);
                def.setExternal(true);
                
                LabkeyScriptEngineManager.saveDefinition(def);
            }
        }
    }
}
