/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Aug 8, 2006
 * Time: 4:21:56 PM
 */
public class StudyFolderType extends DefaultFolderType
{
    public static final String NAME = "Study";

    StudyFolderType(StudyModule module)
    {
        super(NAME,
                "Manage human and animal studies involving long-term observations at distributed sites. " +
                        "Use specimen repository for samples. Design and manage specialized assays. " +
                        "Analyze, visualize and share results.",
                null,
                Arrays.asList(StudyModule.manageStudyPartFactory.createWebPart(),
                        StudyModule.reportsPartFactory.createWebPart(),
                        StudyModule.samplesPartFactory.createWebPart(),
                        StudyModule.datasetsPartFactory.createWebPart(),
                        StudyModule.subjectsWideWebPartFactory.createWebPart()),
                getActiveModulesForOwnedFolder(module), module);

    }

    @Override
    public String getStartPageLabel(ViewContext ctx)
    {
        Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
        return study == null ? "New Study" : study.getLabel();
    }

    @Override
    public HelpTopic getHelpTopic()
    {
        return new HelpTopic("study");
    }

    private static Set<Module> _activeModulesForOwnedFolder = null;
    private synchronized static Set<Module> getActiveModulesForOwnedFolder(StudyModule module)
    {
        if (null != _activeModulesForOwnedFolder)
            return _activeModulesForOwnedFolder;

        Set<Module> active = getDefaultModuleSet();
        active.add(module);
        Set<String> dependencies = module.getModuleDependenciesAsSet();
        for (String moduleName : dependencies)
            active.add(ModuleLoader.getInstance().getModule(moduleName));
       _activeModulesForOwnedFolder = active;
        return active;
    }

    public void addManageLinks(NavTree adminNavTree, Container container)
    {
        adminNavTree.addChild(new NavTree("Manage Study", new ActionURL(StudyController.ManageStudyAction.class, container)));
        adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(container)));
        adminNavTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(container)));
        adminNavTree.addChild(new NavTree("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, container)));
    }
}
