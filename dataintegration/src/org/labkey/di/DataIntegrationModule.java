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

package org.labkey.di;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.di.pipeline.ETLPipelineProvider;
import org.labkey.di.view.DataIntegrationController;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: matthewb
 * Date: 12 Jan 2013
 */
public class DataIntegrationModule extends DefaultModule
{
    public static final String NAME = "DataIntegration";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 0.02;
    }

    protected void init()
    {
        addController("dataintegration", DataIntegrationController.class);
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("dataintegration");
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
    }


    public void doStartup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ETLPipelineProvider(this));
    }
}
