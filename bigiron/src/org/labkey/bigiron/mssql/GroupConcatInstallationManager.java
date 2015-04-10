/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ExceptionUtil;

import java.sql.Connection;

/**
 * User: adam
 * Date: 10/16/13
 * Time: 3:24 PM
 */
public class GroupConcatInstallationManager
{
    private static final String INITIAL_VERISON = "1.00.11845";
    private static final String CURRENT_GROUP_CONCAT_VERSION = "1.00.23696";

    private final @Nullable String _installedVersion;


    private GroupConcatInstallationManager()
    {
        _installedVersion = determineInstalledVersion();
    }

    private @Nullable String determineInstalledVersion()
    {
        return isInstalled() ? getVersion() : null;
    }

    private @NotNull String getVersion()
    {
        try
        {
            SqlSelector selector = new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT core.GroupConcatVersion()");
            selector.setLogLevel(Level.OFF);

            return selector.getObject(String.class);
        }
        catch (Exception e)
        {
            return INITIAL_VERISON;
        }
    }

    private boolean isInstalled()
    {
        return isInstalled(CoreSchema.getInstance().getSchema().getScope());
    }

    @Nullable
    private String getInstalledVersion()
    {
        return _installedVersion;
    }

    private boolean uninstallPrevious(ModuleContext context)
    {
        FileSqlScriptProvider provider = new FileSqlScriptProvider(ModuleLoader.getInstance().getCoreModule());
        SqlScriptRunner.SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, CoreSchema.getInstance().getSchema(), "group_concat_uninstall.sql", "core");

        try
        {
            SqlScriptManager.get(provider, script.getSchema()).runScript(context.getUpgradeUser(), script, context, null);
            return true;
        }
        catch (Throwable t)
        {
            // The GROUP_CONCAT uninstall script can fail if the database user lacks sufficient permissions. If the uninstall
            // fails then log and display the exception to admins, but continue upgrading. Leaving the old version in place
            // is not the end of the world

            // Wrap the exception to provide an explanation to the admin
            Exception wrap = new Exception("Failure uninstalling the existing GROUP_CONCAT aggregate function, which means it can't be upgraded to the latest version. Contact LabKey Software if you need assistance installing the newest version of this function, or see https://www.labkey.org/wiki/home/Documentation/page.view?name=groupconcatinstall", t);
            ExceptionUtil.logExceptionToMothership(null, wrap);
            ModuleLoader.getInstance().addModuleFailure("Core", wrap);

            return false;
        }
    }

    private void install(ModuleContext context, String scriptName)
    {
        FileSqlScriptProvider provider = new FileSqlScriptProvider(ModuleLoader.getInstance().getCoreModule());
        SqlScriptRunner.SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, CoreSchema.getInstance().getSchema(), scriptName, "core");

        try (Connection conn = CoreSchema.getInstance().getSchema().getScope().getUnpooledConnection())
        {
            SqlScriptManager.get(provider, script.getSchema()).runScript(context.getUpgradeUser(), script, context, conn);
        }
        catch (Throwable t)
        {
            // The GROUP_CONCAT install script can fail for a variety of reasons, e.g., the database user lacks sufficient
            // permissions. If the automatic install fails then log and display the exception to admins, but continue
            // upgrading. Not having GROUP_CONCAT is not a disaster; admin can install the function manually later.

            // Wrap the exception to provide an explanation to the admin
            Exception wrap = new Exception("Failure installing GROUP_CONCAT aggregate function. This function is required for optimal operation of this server. Contact LabKey Software if you need assistance installing this function, or see https://www.labkey.org/wiki/home/Documentation/page.view?name=groupconcatinstall", t);
            ExceptionUtil.logExceptionToMothership(null, wrap);
            ModuleLoader.getInstance().addModuleFailure("Core", wrap);
        }
    }

    private boolean isInstalled(String version)
    {
        return version.equals(getInstalledVersion());
    }

    static boolean isInstalled(DbScope scope)
    {
        try
        {
            // Attempt to use the core.GROUP_CONCAT() aggregate function. If this succeeds, we know it's installed.
            SqlExecutor executor = new SqlExecutor(scope);
            executor.setLogLevel(Level.OFF);  // We expect this to fail in many cases... shut off data layer logging
            executor.execute("SELECT x.G, core.GROUP_CONCAT('Foo') FROM (SELECT 1 AS G) x GROUP BY G");

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // This was originally a synchronous upgrade invoked by core-13.22-13.23.sql, see #18600. Then we marked it with
    // @DeferredUpgrade to ensure property manager was completely upgraded, #18979. But this would fail if (e.g.) the
    // core schema was bootstrapped, the server restarted, and module upgrade continued. So this is now called
    // (indirectly) from CoreModule.afterUpdate().
    static void ensureGroupConcat(ModuleContext context)
    {
        GroupConcatInstallationManager manager = new GroupConcatInstallationManager();

        // Return if newest version is already present...
        if (manager.isInstalled(GroupConcatInstallationManager.CURRENT_GROUP_CONCAT_VERSION))
            return;

        boolean success = manager.uninstallPrevious(context);

        // If we can't uninstall the old version then give up; GroupConcatInstallationManager already logged the error
        if (!success)
            return;

        // Attempt to install the new version
        manager.install(context, "group_concat_install_1.00.23696.sql");
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
