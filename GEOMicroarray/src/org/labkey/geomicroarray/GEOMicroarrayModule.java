/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.geomicroarray;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.geomicroarray.query.GEOMicroarrayProviderSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class GEOMicroarrayModule extends DefaultModule
{
    public String getName()
    {
        return "GEOMicroarray";
    }

    public double getVersion()
    {
        return 12.30;
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    protected void init()
    {
        addController("geomicroarray", GEOMicroarrayController.class);
        GEOMicroarrayProviderSchema.register();
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new GEOMicroarrayContainerListener());
        AssayService.get().registerAssayProvider(new GEOMicroarrayAssayProvider());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("geomicroarray");
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.singleton(DbSchema.get("geomicroarray"));
    }
}
