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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.TestUpgradeCodeCounter;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Directive;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.security.AllowedExternalResourceHosts;
import org.labkey.core.security.AllowedExternalResourceHosts.AllowedHost;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;

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
    public void upgradeCode(ModuleContext moduleContext)
    {
        TestUpgradeCodeCounter.incrementCounter();
    }

    /**
     * Remove WithCounter (SampleNameGenCounter-) core.DBSequences records with case-insensitive names, keep the one with the largest Value
     */
    private static void removeDuplicateWithCounterSeqs(Container container)
    {
        TableInfo tableInfo = CoreSchema.getInstance().getTableInfoDbSequences();

        SQLFragment sql = new SQLFragment()
                .append("SELECT RowId, Name, Value \n")
                .append("FROM ").append(tableInfo, "seq")
                .append(" WHERE seq.NAME LIKE 'SampleNameGenCounter-%' AND seq.Container = ?").add(container)
                .append(" ORDER BY Value DESC");

        @NotNull Map<String, Object>[] results = new SqlSelector(tableInfo.getSchema(), sql).getMapArray();
        if (results.length > 0)
        {
            Set<String> seqs = new CaseInsensitiveHashSet();
            Set<Integer> toRemove = new HashSet<>();
            for (Map<String, Object> result : results)
            {
                String seqName = (String) result.get("Name");
                Long seqValue = (Long) result.get("Value");
                Integer seqRowId = asInteger(result.get("RowId"));

                if (seqs.contains(seqName)) // case-insensitive duplicates found
                {
                    LOG.warn("A duplicate withCounter sequence '" + seqName + "' with value '" + seqValue + "' is removed.");
                    toRemove.add(seqRowId);
                }
                else
                    seqs.add(seqName);
            }

            if (!toRemove.isEmpty())
            {
                SQLFragment deleteSql = new SQLFragment("DELETE FROM ").append(tableInfo).append(" WHERE RowId");
                deleteSql = tableInfo.getSqlDialect().appendInClauseSql(deleteSql, toRemove);
                new SqlExecutor(tableInfo.getSchema()).execute(deleteSql);
            }
        }
    }

    private static void toLowerCaseWithCounterSeqs(Container container)
    {
        TableInfo tableInfo = CoreSchema.getInstance().getTableInfoDbSequences();
        SQLFragment toLowerSql = new SQLFragment("UPDATE ").append(tableInfo)
            .append(" SET Name = LOWER(Name) ")
            .append(" WHERE Container = ? AND NAME LIKE 'SampleNameGenCounter-%'")
            .add(container);
        new SqlExecutor(tableInfo.getSchema()).execute(toLowerSql);
    }

    /**
     * Called from core-24.001-24.002.sql to make withCounter naming pattern case-insensitive
     * - For existing duplicate, only the one with the largest 'Value' is retained, to minimize naming conflict.
     * - All withCounter sequence name is then updated to lower case
     */
    @SuppressWarnings("unused")
    public static void makeWithCounterCaseInsensitive(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        TableInfo tableInfo = CoreSchema.getInstance().getTableInfoDbSequences();

        SQLFragment sql = new SQLFragment()
            .append("SELECT DISTINCT Container\n")
            .append("FROM ").append(tableInfo, "seq")
            .append(" WHERE seq.NAME LIKE 'SampleNameGenCounter-%'");

        @NotNull List<String> containers = new SqlSelector(tableInfo.getSchema(), sql).getArrayList(String.class);
        if (containers.isEmpty())
            return;

        for (String containerId : containers)
        {
            Container container = ContainerManager.getForId(containerId);
            if (container == null)
            {
                LOG.warn("Container doesn't exist: " + containerId);
                continue;
            }

            LOG.info("** starting upgrade withCounter DBSequences in container: " + container.getPath());

            removeDuplicateWithCounterSeqs(container);

            toLowerCaseWithCounterSeqs(container);

            LOG.info("** finished upgrade withCounter DBSequences for container: " + container.getPath());
        }
    }

    /**
     * Called from core-25.000-25.001.sql
     */
    @SuppressWarnings("unused")
    public static void migrateAllowedExternalConnectionHosts(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        List<String> hosts = AppProps.getInstance().getExternalSourceHosts();
        List<AllowedHost> allowedHosts = hosts.stream()
            .map(host -> new AllowedHost(Directive.Connection, host))
            .toList();

        // No need to synchronize since upgrade is single-threaded
        AllowedExternalResourceHosts.saveAllowedHosts(allowedHosts, context.getUpgradeUser());
    }

    /**
     * Called from core-25.008-25.009.sql
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
            type.addWhereSql(updateSql, "Parent", "DocumentName");

            new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(updateSql);
        }
    }
}