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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * deployments. This upgrade code removes those duplicates and a subsequent SQL statement switches the column to
     * case-insensitive.
     */
    @SuppressWarnings("unused")
    public static void removeDuplicateModuleEntries(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ModuleLoader ml = ModuleLoader.getInstance();
        Map<String, ModuleContext> duplicateContexts = ml.getUnknownModuleContexts().entrySet().stream()
            .filter(e -> null != ml.getModule(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!duplicateContexts.isEmpty())
        {
            LOG.info("Deleting duplicate entries in core.Modules:");
            duplicateContexts
                .forEach((k, v) -> {
                    LOG.info("Deleting duplicate module context \"" + k + "\"");
                    ml.removeUnknownModuleContext(v);
                });
        }
    }
}