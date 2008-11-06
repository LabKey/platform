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

package org.labkey.xarassay;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayService;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
public class XarAssayModule extends DefaultModule
{

    public XarAssayModule()
    {
        super("XarAssay", 8.30, null, false);
        addController("xarassay", XarAssayController.class);
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);

        AssayService.get().registerAssayProvider(new XarAssayProvider());
//        AssayService.get().registerAssayProvider(new MsFractionAssayProvider());
        AssayService.get().registerAssayProvider(new CptacAssayProvider());
        PipelineService.get().registerPipelineProvider(new XarAssayPipelineProvider());

    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }
}
