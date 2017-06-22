/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.bigiron.mssql;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.bigiron.AbstractClrInstallationManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.ActionURL;
import org.labkey.bigiron.BigIronController;

import java.util.Collection;

/**
 * User: adam
 * Date: 10/16/13
 * Time: 3:24 PM
 *
 *  This was originally a synchronous upgrade invoked by core-13.22-13.23.sql, see #18600. Then we marked it with
 * "@DeferredUpgrade" to ensure property manager was completely upgraded, #18979. But this would fail if (e.g.) the
 * core schema was bootstrapped, the server restarted, and module upgrade continued. So this is now called
 * (indirectly) from CoreModule.afterUpdate().
 *
 * As part of story for spec 28570, which introduced another CLR Assembly for the Premium module, the meat of this
 * class was abstracted into a common superclass.
 */
public class GroupConcatInstallationManager extends AbstractClrInstallationManager
{
    private static final String INITIAL_VERSION = "1.00.11845";
    private static final String CURRENT_GROUP_CONCAT_VERSION = "1.00.23696";
    private static final GroupConcatInstallationManager _instance = new GroupConcatInstallationManager();

    private GroupConcatInstallationManager()
    {
    }

    public static GroupConcatInstallationManager get()
    {
        return _instance;
    }

    @Override
    protected DbSchema getSchema()
    {
        return CoreSchema.getInstance().getSchema();
    }

    @Override
    protected String getModuleName()
    {
        return ModuleLoader.getInstance().getCoreModule().getName();
    }

    @Override
    protected String getBaseScriptName()
    {
        return "group_concat";
    }

    @Override
    protected String getInitialVersion()
    {
        return INITIAL_VERSION;
    }

    @Override
    protected String getCurrentVersion()
    {
        return CURRENT_GROUP_CONCAT_VERSION;
    }

    @Override
    protected String getInstallationExceptionMsg()
    {
        return "Failure installing GROUP_CONCAT aggregate function. This function is required for optimal operation of this server. Contact LabKey if you need assistance installing this function, or see https://www.labkey.org/wiki/home/Documentation/page.view?name=groupconcatinstall";
    }

    @Override
    protected String getUninstallationExceptionMsg()
    {
        return "Failure uninstalling the existing GROUP_CONCAT aggregate function, which means it can't be upgraded to the latest version. Contact LabKey if you need assistance installing the newest version of this function, or see https://www.labkey.org/wiki/home/Documentation/page.view?name=groupconcatinstall";
    }

    @Override
    protected String getInstallationCheckSql()
    {
        return "SELECT x.G, core.GROUP_CONCAT('Foo') FROM (SELECT 1 AS G) x GROUP BY G";
    }

    @Override
    protected String getVersionCheckSql()
    {
        return "SELECT core.GroupConcatVersion()";
    }

    @Override
    protected void addAdminWarningMessages(Collection<String> messages)
    {
        ActionURL downloadURL = new ActionURL(BigIronController.DownloadGroupConcatInstallScriptAction.class, ContainerManager.getRoot());
        messages.add("The GROUP_CONCAT aggregate function is not installed. This function is required for optimal operation of this server. <a href=\"" + downloadURL + "\">Download installation script.</a> " + new HelpTopic("groupconcatinstall").getSimpleLinkHtml("View installation instructions."));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
            {
                GroupConcatInstallationManager mgr = new GroupConcatInstallationManager();
                assertTrue(mgr.isInstalled());
                assertTrue(mgr.isInstalled(CURRENT_GROUP_CONCAT_VERSION));
                assertEquals(CURRENT_GROUP_CONCAT_VERSION, mgr.getVersion());
                assertEquals(CURRENT_GROUP_CONCAT_VERSION, mgr.determineInstalledVersion());
                assertEquals(CURRENT_GROUP_CONCAT_VERSION, mgr.getInstalledVersion());
            }
        }
    }
}
