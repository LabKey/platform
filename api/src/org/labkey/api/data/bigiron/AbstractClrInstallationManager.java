/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.data.bigiron;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.Warnings;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.Connection;

/**
 * Base class for handling installation of CLR Assemblies on SQL Server. Most methods/logic
 * were abstracted from what used to be in GroupConcatInstallationManager.
 *
 * User: tgaluhn
 * Date: 1/13/2017
 */
public abstract class AbstractClrInstallationManager
{
    private static final Logger LOG = LogManager.getLogger(AbstractClrInstallationManager.class);

    @Nullable
    private String _installedVersion = null;

    @Nullable
    protected String determineInstalledVersion()
    {
        return isInstalled() ? getVersion() : null;
    }

    @NotNull
    protected String getVersion()
    {
        try
        {
            SqlSelector selector = new SqlSelector(getSchema(), getVersionCheckSql());
            selector.setLogLevel(Level.OFF);

            return selector.getObject(String.class);
        }
        catch (Exception e)
        {
            return getInitialVersion();
        }
    }

    protected boolean isInstalled()
    {
        return isInstalled(getSchema().getScope());
    }

    @Nullable
    protected String getInstalledVersion()
    {
        if (_installedVersion == null)
        {
            _installedVersion = determineInstalledVersion();
        }
        return _installedVersion;
    }

    private boolean uninstallPrevious(ModuleContext context)
    {
        FileSqlScriptProvider provider = new FileSqlScriptProvider(ModuleLoader.getInstance().getModule(getModuleName()));
        SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, getSchema(), getBaseScriptName() + "_uninstall.sql", getSchema().getName());

        try
        {
            SqlScriptManager.get(provider, script.getSchema()).runScript(context.getUpgradeUser(), script, context, null);
            return true;
        }
        catch (Throwable t)
        {
            // The uninstall script can fail if the database user lacks sufficient permissions. If the uninstall
            // fails then log and display the exception to admins, but continue upgrading. Leaving the old version in place
            // is not the end of the world

            // Wrap the exception to provide an explanation to the admin
            Exception wrap = new Exception(getUninstallationExceptionMsg(), t);
            ExceptionUtil.logExceptionToMothership(null, wrap);
            ModuleLoader.getInstance().addModuleFailure(getModuleName(), wrap);

            return false;
        }
    }

    private void install(ModuleContext context)
    {
        boolean rds = getSchema().getScope().isRds();
        SqlScript script = getInstallScript(!rds);

        try (Connection conn = getSchema().getScope().getUnpooledConnection())
        {
            LOG.info("Executing " + script.getDescription() + " against " + (rds ? "an RDS" : "a non-RDS") + " database");
            SqlScriptManager.get(script.getProvider(), script.getSchema()).runScript(context.getUpgradeUser(), script, context, conn);
        }
        catch (Throwable t)
        {
            // The install script can fail for a variety of reasons, e.g., the database user lacks sufficient
            // permissions. If the automatic install fails then log and display the exception to admins, but continue
            // upgrading. Not having the assembly is not a disaster; admin can install the function manually later.

            // Wrap the exception to provide an explanation to the admin
            Exception wrap = new Exception(getInstallationExceptionMsg(), t);
            ExceptionUtil.logExceptionToMothership(null, wrap);
            ModuleLoader.getInstance().addModuleFailure(getModuleName(), wrap);
        }
    }

    @NotNull
    public SqlScript getInstallScript()
    {
        return getInstallScript(true);
    }

    @NotNull
    public SqlScript getInstallScript(boolean includeEnableClrStatements)
    {
        String scriptName = getBaseScriptName() +  "_install_" + getCurrentVersion() + ".sql";
        FileSqlScriptProvider provider = new ClrInstallationScriptProvider(ModuleLoader.getInstance().getModule(getModuleName()), includeEnableClrStatements);
        return new FileSqlScriptProvider.FileSqlScript(provider, getSchema(), scriptName, getSchema().getName());
    }

    protected boolean isInstalled(String version)
    {
        return version.equals(getInstalledVersion());
    }

    public boolean isInstalled(DbScope scope)
    {
        boolean result = true;
        try
        {
            // Attempt to use a function in the assembly. If this succeeds, we know it's installed.
            SqlExecutor executor = new SqlExecutor(scope);
            executor.setLogLevel(Level.OFF);  // We expect this to fail in many cases... shut off data layer logging
            executor.execute(getInstallationCheckSql());
        }
        catch (Exception e)
        {
            result = false;
        }
        return result;
    }

    public void ensureInstalled(ModuleContext context)
    {
        getInstalledVersion();
        // Return if newest version is already present...
        if (isInstalled(getCurrentVersion()))
            return;

        boolean success = uninstallPrevious(context);

        // If we can't uninstall the old version then give up; error has already been logged
        if (!success)
            return;

        // Attempt to install the new version
        install(context);
    }

    // Helper to generate standard warning message
    protected void addAdminWarningMessage(Warnings warnings, String text, Class<? extends Controller> downloadActionClass, @Nullable String helpTopic)
    {
        ActionURL downloadURL = new ActionURL(downloadActionClass, ContainerManager.getRoot());
        HtmlStringBuilder builder = HtmlStringBuilder.of(text + " ");
        builder.append(new Link.LinkBuilder("Download installation script.").href(downloadURL).clearClasses().getHtmlString());

        if (null != helpTopic)
            builder.append(" ").append(new HelpTopic(helpTopic).getSimpleLinkHtml("View installation instructions."));

        warnings.add(builder);
    }

    private static class ClrInstallationScriptProvider extends FileSqlScriptProvider
    {
        private final boolean _includeEnableClrStatements;

        public ClrInstallationScriptProvider(Module module, boolean includeEnableClrStatements)
        {
            super(module);
            _includeEnableClrStatements = includeEnableClrStatements;
        }

        @Override
        protected String getContents(DbSchema schema, String filename) throws SqlScriptException
        {
            String substitution = _includeEnableClrStatements ? getEnableClrStatements(schema.getSqlDialect()) : "";
            return super.getContents(schema, filename).replace("@@ENABLE_CLR_STATEMENTS@@", substitution);
        }

        private String getEnableClrStatements(SqlDialect dialect) throws SqlScriptException
        {
            Module core = ModuleLoader.getInstance().getCoreModule();
            String enableClrFilename = "enable_clr.sql";
            Path path = Path.parse(core.getSqlScriptsPath(dialect)).append(enableClrFilename);
            Resource r = core.getModuleResource(path);

            try
            {
                return PageFlowUtil.getStreamContentsAsString(r.getInputStream());
            }
            catch (IOException e)
            {
                throw new SqlScriptException(e, enableClrFilename);
            }
        }
    }

    protected abstract DbSchema getSchema();
    protected abstract String getModuleName();
    protected abstract String getBaseScriptName();
    protected abstract String getInitialVersion();
    protected abstract String getCurrentVersion();
    protected abstract String getInstallationExceptionMsg();
    protected abstract String getUninstallationExceptionMsg();
    protected abstract String getInstallationCheckSql();
    protected abstract String getVersionCheckSql();
    protected abstract void addAdminWarningMessages(Warnings warnings);
}
