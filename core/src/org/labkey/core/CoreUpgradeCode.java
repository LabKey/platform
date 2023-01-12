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
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: adam
 * Date: Nov 21, 2008
 * Time: 9:57:49 PM
 */
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
}