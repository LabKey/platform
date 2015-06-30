/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.redcap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by klum on 1/16/2015.
 */
public class RedcapModule extends DefaultModule
{
    public static final String NAME = "REDCap";
    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.20;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController("redcap", RedcapController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        StudyService.get().registerStudyReloadSource(new RedcapReloadSource());

        //add the REDCap import task to the list of system maintenance tasks
        SystemMaintenance.addTask(new RedcapMaintenanceTask());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}
