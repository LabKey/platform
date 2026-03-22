/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.TestUpgradeCodeCounter;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Directive;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.security.AllowedExternalResourceHosts;
import org.labkey.core.security.AllowedExternalResourceHosts.AllowedHost;

import java.util.List;

public class CoreUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogHelper.getLogger(CoreUpgradeCode.class, "Custom core upgrade steps");

    // We don't call ContainerManager.getRoot() during upgrade code since the container table may not yet match
    // ContainerManager's assumptions. For example, older installations don't have a description column until
    // the 10.1 scripts run (see #9927).
    @SuppressWarnings("UnusedDeclaration")
    private String getRootId()
    {
        return new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT EntityId FROM core.Containers WHERE Parent IS NULL").getObject(String.class);
    }

    // Not currently invoked, but available for future scripts
    @SuppressWarnings({"UnusedDeclaration"})
    public void handleUnknownModules(ModuleContext context)
    {
        ModuleLoader.getInstance().handleUnknownModules();
    }

    /** Java upgrade method used for testing purposes (see PG and MSSQL InlineProcedureTestCase) */
    @SuppressWarnings({"UnusedDeclaration"})
    public void testUpgradeCode(ModuleContext moduleContext)
    {
        TestUpgradeCodeCounter.incrementCounter();
    }

    /**
     * Called from core-25.000-25.001.sql
     */
    @SuppressWarnings("unused")
    public static void migrateAllowedExternalConnectionHosts(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // TODO: Remove getExternalSourceHosts() method when this upgrade code is deleted
        List<String> hosts = AppProps.getInstance().getExternalSourceHosts();
        List<AllowedHost> allowedHosts = hosts.stream()
            .map(host -> new AllowedHost(Directive.Connection, host))
            .toList();

        // No need to synchronize since upgrade is single-threaded
        AllowedExternalResourceHosts.saveAllowedHosts(allowedHosts, context.getUpgradeUser());
    }

    /**
     * Called from core-25.008-25.009.sql
     * Called from core-26.000-26.001.sql
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public static void populateAttachmentParentTypeColumn(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        for (AttachmentParentType type : AttachmentService.get().getAttachmentParentTypes())
        {
            LOG.info("Populating attachment parent type for {}", type.getUniqueName());

            SQLFragment updateSql = new SQLFragment("UPDATE ")
                .append(CoreSchema.getInstance().getTableInfoDocuments())
                .append(" SET ParentType = ?")
                .add(type.getUniqueName())
                .append(" WHERE ");
            // TODO: This is the only caller of addWhereSql(), which can be removed when this upgrade code is deleted.
            type.addWhereSql(updateSql, "Parent", "DocumentName");

            new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(updateSql);
        }
    }

    /**
     * Called from core-26.002-26.003.sql
     */
    @DeferredUpgrade // Need to execute this after AttachmentTypes are registered
    @SuppressWarnings("unused")
    public static void deleteOrphanedAttachments(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        AttachmentService svc = AttachmentService.get();

        if (svc != null)
        {
            svc.deleteOrphanedAttachments();
        }
    }
}