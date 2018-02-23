/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.audit;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.audit.query.AuditQuerySchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class AuditModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(AuditModule.class);

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return true;
    }

    public String getName()
    {
        return "Audit";
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        AuditLogService.registerProvider(AuditLogImpl.get());
        addController("audit", AuditController.class);
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public void doStartup(ModuleContext moduleContext)
    {
        AuditQuerySchema.register(this);
        AuditLogService.get().registerAuditType(new SiteSettingsAuditProvider());

        AuditController.registerAdminConsoleLinks();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return getProvisionedSchemaNames();
    }

    @Override
    @NotNull
    public Set<String> getProvisionedSchemaNames()
    {
        return Collections.singleton(AuditSchema.SCHEMA_NAME);
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new AuditUpgradeCode();
    }
}
