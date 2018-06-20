/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.demo;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.demo.model.DemoManager;
import org.labkey.demo.model.Person;
import org.labkey.demo.view.DemoWebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;


public class DemoModule extends DefaultModule
{
    public String getName()
    {
        return "Demo";
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        addController("demo", DemoController.class);
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Collections.singletonList(new BaseWebPartFactory("Demo Summary", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
        {
            {
                addLegacyNames("Narrow Demo Summary");
            }

            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                return new DemoWebPart();
            }
        }));
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        Person[] people = DemoManager.getInstance().getPeople(c);
        if (people != null && people.length > 0)
        {
            Collection<String> list = new LinkedList<>();
            list.add("Demo Module: " + people.length + " person records.");
            return list;
        }
        return Collections.emptyList();
    }

    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new DemoContainerListener());
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(DemoSchema.getInstance().getSchemaName());
    }
}