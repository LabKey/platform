/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

package org.labkey.lab;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.lab.audit.LabEventsAuditViewFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class LabModule extends DefaultModule
{
    @Override
    public String getName()
    {
        return "Lab";
    }

    @Override
    public double getVersion()
    {
        return 13.10;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new AlwaysAvailableWebPartFactory("Lab History", WebPartFactory.LOCATION_BODY, false, false)
            {
                public WebPartView getWebPartView(final ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                {
                    JspView view = new JspView<>("/org/labkey/lab/history.jsp", webPart);
                    view.setTitle("Lab History");
                    return view;
                }
            })
        );
    }

    @Override
    protected void init()
    {
        addController("lab", LabController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ModuleLoader.getInstance().registerFolderType(this, new LabFolderType(this));

        AuditLogService.get().addAuditViewFactory(LabEventsAuditViewFactory.getInstance());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("lab");
    }

    @NotNull
    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }
}