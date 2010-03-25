/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.study;

import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.view.Portal;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.exp.list.ListService;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.assay.AssayController;

import java.util.Set;
import java.util.Collections;
import java.util.Arrays;

/**
 * User: kevink
 * Date: Oct 9, 2009
 */
public class AssayFolderType extends DefaultFolderType
{
    public static final String NAME = "Assay";

    AssayFolderType(StudyModule module)
    {
        super(NAME,
                "Design and manage specialized assays. " +
                "Analyze, visualize and share results.",
                Collections.<Portal.WebPart>emptyList(),
                Arrays.asList(StudyModule.assayListWebPartFactory.createWebPart()),
                getActiveModulesForOwnedFolder(module),
                module);
    }

    @Override
    public AppBar getAppBar(ViewContext context)
    {
        AssayController controller = new AssayController();
        controller.setViewContext(context);
        return controller.getAppBar(null);
    }

    protected static Set<Module> getActiveModulesForOwnedFolder(StudyModule module)
    {
        ModuleLoader ml = ModuleLoader.getInstance();
        Set<Module> active = getDefaultModuleSet();
        active.add(module);
        Set<String> dependencies = module.getModuleDependenciesAsSet();
        for (String moduleName : dependencies)
            active.add(ml.getModule(moduleName));

        //add the pipeline module as well so the Data Pipeline web part is available
        active.add(ml.getModule(PipelineService.MODULE_NAME));
        
        return active;
    }

}
