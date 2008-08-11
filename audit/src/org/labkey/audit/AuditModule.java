/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.audit.query.AuditQuerySchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class AuditModule extends DefaultModule
{
    public static final String NAME = "Audit";
    private static final Logger _log = Logger.getLogger(AuditModule.class);

    public AuditModule()
    {
        super(NAME, 8.22, null, true);
        AuditLogService.registerProvider(AuditLogImpl.get());
        addController("audit", AuditController.class);
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:

        AuditQuerySchema.register();
        AuditLogService.get().addAuditViewFactory(new SiteSettingsAuditViewFactory());
        AuditController.registerAdminConsoleLinks();
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("audit");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(AuditSchema.getInstance().getSchema());
    }
}