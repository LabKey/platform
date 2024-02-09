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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.UpgradeUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PasswordRule;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.login.DbLoginManager;
import org.labkey.core.login.LoginController.SaveDbLoginPropertiesForm;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Invoked at bootstrap time to set the projects that are excluded from project locking by default
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public void setDefaultExcludedProjects(ModuleContext context)
    {
        List<GUID> guids = Stream.of("home", "Shared")
            .map(ContainerManager::getForPath)
            .filter(Objects::nonNull)
            .map(Container::getEntityId)
            .collect(Collectors.toList());
        ContainerManager.setExcludedProjects(guids, () -> {});
    }

    /**
     * Called from core-23.000-23.001.sql on PostgreSQL only. The core.Modules.Name column has been case-sensitive on
     * PostgreSQL, which has allowed "duplicate" (differing only in case) module names to creep into the column on some
     * deployments. This upgrade code de-duplicates the names and a subsequent SQL statement switches the column to
     * case-insensitive.
     */
    @SuppressWarnings("unused")
    public static void deduplicateModuleEntries(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ModuleLoader ml = ModuleLoader.getInstance();
        MultiValuedMap<String, ModuleContext> multiMap = new CaseInsensitiveArrayListValuedMap<>();

        // Add every module context to the multivalued map, with known modules first
        Set<String> unknown = ml.getUnknownModuleContexts().keySet();
        ml.getAllModuleContexts().stream()
            .sorted(Comparator.comparing(ctx -> unknown.contains(ctx.getName())))  // false < true
            .forEach(ctx -> multiMap.put(ctx.getName(), ctx));

        // For each canonical name, de-duplicate every context after the first one
        multiMap.asMap().values().stream()
            .filter(contexts -> contexts.size() > 1)
            .forEach(contexts -> {
                MutableInt counter = new MutableInt(1);
                contexts.stream()
                    .skip(1)
                    .forEach(ctx -> {
                        String oldName = ctx.getName();
                        String newName = oldName + "_" + counter.incrementAndGet();
                        LOG.info("De-duplicating module context \"" + oldName + "\" to \"" + newName + "\"");
                        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(new SQLFragment("UPDATE core.Modules SET Name = ? WHERE Name = ?", newName, oldName));
                    });
            });
    }

    /**
     * Called from prop-23.008-23.009.sql to explicitly set the default database authentication properties to
     * Strength = Good, Expiration = Never on existing servers where the strength property has never been saved
     * (and therefore would have defaulted to Strength = Weak, which no longer exists). This ensures that these
     * servers don't switch to the new default, Strength = Strong, on upgrade.
     */
    @SuppressWarnings("unused")
    public static void saveNullStrengthAsWeak(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        LOG.info("Upgrading an existing installation and checking current password strength");
        Map<String, String> properties = DbLoginManager.getProperties();
        String strength = properties.get(DbLoginManager.Key.Strength.toString());
        if (null == strength)
        {
            LOG.info("Password strength has not been saved; setting to Good.");
            SaveDbLoginPropertiesForm form = new SaveDbLoginPropertiesForm();
            form.setStrength(PasswordRule.Good.toString());
            form.setExpiration(properties.getOrDefault(DbLoginManager.Key.Expiration.toString(), PasswordExpiration.Never.toString()));
            DbLoginManager.saveProperties(context.getUpgradeUser(), form);
        }
        else
        {
            LOG.info("Password strength is currently set to " + strength + "; taking no action.");
        }
    }

    /**
     * Called from core-23.009-23.010.sql to assign the SiteAdminRole to the Site Admin group in the root security
     * policy, allowing us to remove most special handling for this group.
     */
    @SuppressWarnings("unused")
    public static void assignSiteAdminRole(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // Can't execute this synchronously since savePolicy() references core.Users view but annotating with
        // @DeferredUpgrade runs it too late, since site admins won't be able to monitor the upgrade process.
        CoreModule._afterUpdateRunnable = () -> {
            Container root = ContainerManager.getRoot();
            Group siteAdminGroup = SecurityManager.getGroup(Group.groupAdministrators);
            if (null != siteAdminGroup)
            {
                MutableSecurityPolicy policy = new MutableSecurityPolicy(root.getPolicy());
                policy.addRoleAssignment(siteAdminGroup, RoleManager.getRole(SiteAdminRole.class));
                SecurityPolicyManager.savePolicy(policy, User.getAdminServiceUser());
            }
            else
            {
                LOG.warn("Site Admin group does not exist!");
            }
        };
    }

    /**
     * Called from core-23.010-23.011.sql to uniquify core.Principals.Name in a case-insensitive manner to allow
     * adding a case-insensitive unique index on PostgreSQL.
     */
    @SuppressWarnings("unused")
    public static void uniquifyPrincipalsName(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ColumnInfo name = CoreSchema.getInstance().getTableInfoPrincipals().getColumn("Name");
        ColumnInfo ownerId = CoreSchema.getInstance().getTableInfoPrincipals().getColumn("OwnerId");
        ColumnInfo type = CoreSchema.getInstance().getTableInfoPrincipals().getColumn("Type");
        UpgradeUtils.uniquifyValues(name, ownerId, new SimpleFilter(type.getFieldKey(), "g"), new Sort("UserId"), false, false);
        UpgradeUtils.uniquifyValues(name, ownerId, new SimpleFilter(type.getFieldKey(), "u"), new Sort("UserId"), false, false);
        UpgradeUtils.uniquifyValues(name, ownerId, new SimpleFilter(type.getFieldKey(), "m"), new Sort("UserId"), false, false);
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
                Integer seqRowId = (Integer) result.get("RowId");

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
}