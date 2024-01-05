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

package org.labkey.mothership;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.mothership.query.MothershipSchema;
import org.labkey.mothership.view.ExceptionListWebPart;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MothershipModule extends DefaultModule
{
    public static final String NAME = "Mothership";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 24.000;
    }

    @Override
    protected void init()
    {
        addController("mothership", MothershipController.class);
        MothershipSchema.register(this);
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new BaseWebPartFactory("Exception List", false, false)
            {
                @Override
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, Portal.@NotNull WebPart webPart)
                {
                    return new ExceptionListWebPart(portalCtx.getUser(), portalCtx.getContainer(), null);
                }
            }
        );
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        Container c = ContainerManager.ensureContainer(MothershipReport.CONTAINER_PATH, User.getAdminServiceUser());
        Set<Module> modules = new HashSet<>(c.getActiveModules(moduleContext.getUpgradeUser()));
        modules.add(this);
        c.setActiveModules(modules, moduleContext.getUpgradeUser());
        c.setDefaultModule(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(MothershipManager.get().getSchemaName());
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return PageFlowUtil.set(ExceptionStackTrace.TestCase.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        MothershipReport.setShowSelfReportExceptions(true);

        ContainerManager.addContainerListener(new ContainerManager.AbstractContainerListener()
        {
            @Override
            public void containerDeleted(Container c, User user)
            {
                MothershipManager.get().deleteForContainer(c);
            }
        });

        UserManager.addUserListener(new UserManager.UserListener()
        {
            @Override
            public void userDeletedFromSite(User user)
            {
                MothershipManager.get().deleteForUser(user);
            }
        });
    }
}
