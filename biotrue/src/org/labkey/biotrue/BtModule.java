/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.biotrue;

import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.biotrue.controllers.BtController;
import org.labkey.biotrue.controllers.BtOverviewWebPart;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.query.BtSchema;
import org.labkey.biotrue.task.BtThreadPool;
import org.labkey.biotrue.task.ScheduledTask;

import java.util.Collections;
import java.util.Set;
import java.util.Collection;
import java.util.Arrays;

public class BtModule extends DefaultModule
{
    public String getName()
    {
        return "BioTrue";
    }

    public double getVersion()
    {
        return 9.10;
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(BtOverviewWebPart.FACTORY);
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected void init()
    {
        addController("biotrue", BtController.class);
        DefaultSchema.registerProvider("biotrue", BtSchema.PROVIDER);
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("biotrue");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(BtManager.get().getSchema());
    }

    public void startup(ModuleContext moduleContext)
    {
        BtThreadPool.get();
        ScheduledTask.startTimer();
    }
}
