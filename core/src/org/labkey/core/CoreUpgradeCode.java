/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AbstractSettingsGroup;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.Portal;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UsersDomainKind;

import java.sql.Connection;
import java.sql.ResultSet;
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
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
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
                    SqlExecutor executor = new SqlExecutor(CoreSchema.getInstance().getSchema());
                    executor.setLogLevel(Level.OFF);  // We expect this to fail in most cases... shut off data layer logging
                    executor.execute("SELECT x.G, core.GROUP_CONCAT('Foo') FROM (SELECT 1 AS G) x GROUP BY G");
                    return;
                }
                catch (Exception e)
                {
                    //
                }

                FileSqlScriptProvider provider = new FileSqlScriptProvider((DefaultModule)ModuleLoader.getInstance().getCoreModule());
                SqlScriptRunner.SqlScript script = new FileSqlScriptProvider.FileSqlScript(provider, "group_concat_install.sql", "core");

                try (Connection conn = CoreSchema.getInstance().getSchema().getScope().getUnpooledConnection())
                {
                    SqlScriptManager.runScript(context.getUpgradeUser(), script, context, conn);
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

        Map<String, String> props = PropertyManager.getProperties(AbstractSettingsGroup.SITE_CONFIG_USER, ContainerManager.getRoot(), "SiteConfig");

        String interval = props.get("systemMaintenanceInterval");
        Set<String> enabled = new HashSet<>();

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

        DbSchema schema = CoreSchema.getInstance().getSchema();
        Collection<Portal.PortalPage> pages = new SqlSelector(schema, "SELECT * FROM core.PortalPages").getCollection(Portal.PortalPage.class);
        String updateSql = "UPDATE core.PortalPages SET EntityId=? WHERE Container=? AND PageId=?";

        SqlExecutor executor = new SqlExecutor(schema);

        for (Portal.PortalPage p : pages)
        {
            if (null != p.getEntityId())
                continue;
            executor.execute(updateSql, GUID.makeGUID(), p.getContainer().toString(), p.getPageId());
        }
    }


    // invoked by core-13.13-13.14.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void setPortalPageUniqueIndexes(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        DbSchema schema = CoreSchema.getInstance().getSchema();
        Collection<String> containers = new SqlSelector(schema, "SELECT EntityId FROM core.Containers").getCollection(String.class);
        for (String c : containers)
        {
            resetPageIndexes(schema, c);
        }
    }

    public static class PortalUpgradePage
    {
        private GUID containerId;
        private String pageId;
        private int index;

        public GUID getContainer()
        {
            return containerId;
        }

        public void setContainer(GUID containerId)
        {
            this.containerId = containerId;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }
    }

    private static void resetPageIndexes(DbSchema schema, String containerEntityId)
    {
        Filter filter = new SimpleFilter(FieldKey.fromString("Container"), containerEntityId);
        Sort sort = new Sort("Index");
        TableInfo portalTable = schema.getTable("PortalPages");
        Set<String> columnNames = new HashSet<>(3);
        columnNames.add("Container");
        columnNames.add("Index");
        columnNames.add("PageId");
        TableSelector ts = new TableSelector(portalTable, columnNames, filter, sort);
        Collection<PortalUpgradePage> pages = ts.getCollection(PortalUpgradePage.class);
        if (pages.size() > 0)
        {
            try
            {
                int validPageIndex = 1;
                for (PortalUpgradePage page : pages)
                {
                    if (validPageIndex != page.getIndex())
                    {
                        page.setIndex(validPageIndex);
                        Table.update(null, portalTable, page, new Object[] {page.getContainer(), page.getPageId()});
                    }
                    validPageIndex++;
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    // invoked by core-13.14-13.15.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateWorkbookSortOrderAndName(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        final DbSchema schema = CoreSchema.getInstance().getSchema();
        final String updateSql = "UPDATE core.Containers SET SortOrder = ?, Name = ? WHERE Parent = ? AND RowId = ?";
        final SqlExecutor updateExecutor = new SqlExecutor(schema);

        String selectSql = "SELECT Parent, RowId FROM core.Containers WHERE Type = 'workbook' ORDER BY Parent, RowId";

        new SqlSelector(schema, selectSql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Container parent = ContainerManager.getForId(rs.getString(1));

                if (null != parent)
                {
                    int rowId = rs.getInt(2);
                    int sortOrder = DbSequenceManager.get(parent, ContainerManager.WORKBOOK_DBSEQUENCE_NAME).next();
                    updateExecutor.execute(updateSql, sortOrder, String.valueOf(sortOrder), parent, rowId);
                }
            }
        });
    }
}
