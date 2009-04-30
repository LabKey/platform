/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.workbook;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.WebPartFactory;
import org.labkey.workbook.view.WorkbookList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class WorkbookModule extends DefaultModule
{
    public final static String EXPERIMENT_DESCRIPTION_WIKI_NAME = "Experiment Description";

    public String getName()
    {
        return "Workbook";
    }

    public double getVersion()
    {
        return 0.01;
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new DefaultWebPartFactory("Workbooks", "menubar", WorkbookList.class));
    }

    protected void init()
    {
        addController("workbook", WorkbookController.class);
    }

    public void startup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new WorkbookContainerListener());
        ModuleLoader.getInstance().registerFolderType(new WorkbookFolderType());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("workbook");
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(WorkbookSchema.getInstance().getSchema());
    }
}