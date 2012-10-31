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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.Portal;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UsersDomainKind;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
                try
                {
                    // Attempt to use the core.GROUP_CONCAT() aggregate function. If this succeeds, we'll skip the install step.
                    SqlExecutor executor = new SqlExecutor(CoreSchema.getInstance().getSchema(), new SQLFragment("SELECT x.G, core.GROUP_CONCAT('Foo') FROM (SELECT 1 AS G) x GROUP BY G"));
                    executor.setLogLevel(Level.OFF);  // We expect this to fail in most cases... shut off data layer logging
                    executor.execute();
                    return;
                }
                catch (Exception e)
                {
                    //
                }

                FileSqlScriptProvider provider = new FileSqlScriptProvider((DefaultModule)ModuleLoader.getInstance().getCoreModule());
                SqlScriptRunner.SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, "group_concat_install.sql", "core");

                Connection conn = CoreSchema.getInstance().getSchema().getScope().getUnpooledConnection();

                try
                {
                    SqlScriptManager.runScript(context.getUpgradeUser(), script, context, conn);
                }
                finally
                {
                    conn.close();
                }
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

    // invoked by core-12.21-12.22.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void ensureCoreUserPropertyDescriptors(ModuleContext context)
    {
        String domainURI = UsersDomainKind.getDomainURI("core", CoreQuerySchema.USERS_TABLE_NAME, UsersDomainKind.getDomainContainer(), context.getUpgradeUser());
        Domain domain = PropertyService.get().getDomain(UsersDomainKind.getDomainContainer(), domainURI);

        if (domain == null)
            domain = PropertyService.get().createDomain(UsersDomainKind.getDomainContainer(), domainURI, CoreQuerySchema.USERS_TABLE_NAME);

        if (domain != null)
            UsersDomainKind.ensureDomainProperties(domain, context.getUpgradeUser(), context.isNewInstall());
    }

    // invoked by core-12.22-12.23.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade  // This needs to happen later, after all of the MaintenanceTasks have been registered
    public void migrateSystemMaintenanceSettings(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        Map<String, String> props = PropertyManager.getProperties(-1, ContainerManager.getRoot(), "SiteConfig");

        String interval = props.get("systemMaintenanceInterval");
        Set<String> enabled = new HashSet<String>();

        for (SystemMaintenance.MaintenanceTask task : SystemMaintenance.getTasks())
            if (!task.canDisable() || !"never".equals(interval))
                enabled.add(task.getName());

        String time = props.get("systemMaintenanceTime");

        if (null == time)
            time = "2:00";

        SystemMaintenance.setProperties(enabled, time);
    }


    /* called at 12.2->12.3 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPortalPageEntityId(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            DbSchema schema = CoreSchema.getInstance().getSchema();
            Collection<Portal.PortalPage> pages = new SqlSelector(schema, "SELECT * FROM core.PortalPages").getCollection(Portal.PortalPage.class);
            String updateSql = "UPDATE core.PortalPages SET EntityId=? WHERE Container=? AND PageId=?";

            for (Portal.PortalPage p : pages)
            {
                if (null != p.getEntityId())
                    continue;
                Table.execute(schema, updateSql,  GUID.makeGUID(), p.getContainer().toString(), p.getPageId());
            }
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }
}
