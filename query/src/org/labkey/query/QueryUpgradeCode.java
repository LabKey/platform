/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.security.UserManager;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.Collections;
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

    // Invoked from script query-9.20-9.21.sql
    public void populateDataSourceColumn(ModuleContext ctx) throws SQLException
    {
        if (!ctx.isNewInstall())
        {
            // Get the DataSource name associated with the core schema (in most cases, "labkeyDataSource")
            String dataSourceName = DbScope.getLabkeyScope().getDataSourceName();

            // Update all existing DbUserSchema rows with labkey DataSource name
            SQLFragment sql = new SQLFragment("UPDATE " + QueryManager.get().getTableInfoDbUserSchema() + " SET DataSource = ?", Collections.<Object>singletonList(dataSourceName));
            Table.execute(QueryManager.get().getDbSchema(), sql);
        }
    }
}
