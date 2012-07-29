/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.core;

import org.apache.log4j.Logger;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(CoreUpgradeCode.class);

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    private String getRootId()
    {
        try
        {
            return Table.executeSingleton(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL", null, String.class);
        }
        catch (SQLException e)
        {
            return null;
        }
    }

    // invoked by prop-10.20-10.21.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateEmailTemplates(ModuleContext context)
    {
        // Change the replacement delimeter character and change to a different PropertyManager node
        if (!context.isNewInstall())
        {
            EmailTemplateService.get().upgradeTo102();
        }
    }

    // invoked by core-11.20-11.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnkownModules();
    }

    // invoked by core-12.10-12.20.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void installGroupConcat(ModuleContext context)
    {
        try
        {
            SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();

            // Run the install script only if dialect supports GROUP_CONCAT
            if (dialect.isSqlServer() && dialect.supportsGroupConcat())
            {
                FileSqlScriptProvider provider = new FileSqlScriptProvider((DefaultModule)ModuleLoader.getInstance().getCoreModule());
                SqlScriptRunner.SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, "group_concat_install.sql", "core");

                Connection conn = CoreSchema.getInstance().getSchema().getScope().getUnpooledConnection();
                SqlScriptManager.runScript(context.getUpgradeUser(), script, context, conn);
                conn.close();
            }
        }
        catch (Throwable t)
        {
            // The GROUP_CONCAT install script can fail for a variety of reasons, e.g., the database user lacks sufficient
            // permissions. If the automatic install fails then log and display the exception to admins, but continue
            // upgrading. Not having GROUP_CONCAT is not a disaster; admin can install the function manually later.

            // Wrap the exception to provide an explanation to the admin
            Exception wrap = new Exception("Failure installing GROUP_CONCAT aggregate function. This function is required for optimal operation of this server. Contact LabKey if you need assistance installing this function.", t);
            ExceptionUtil.logExceptionToMothership(null, wrap);
            ModuleLoader.getInstance().addModuleFailure("Core", wrap);
        }
    }
}
