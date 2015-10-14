/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyFolderTabs;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.StudyManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Aug 8, 2006
 * Time: 4:21:56 PM
 */
public class StudyFolderType extends MultiPortalFolderType
{
    public static final String NAME = "Study";

    private static final List<FolderTab> PAGES = Arrays.asList(
            new StudyFolderTabs.OverviewPage("Overview"),
            new StudyFolderTabs.ParticipantsPage("Participants"),
            new StudyFolderTabs.DataAnalysisPage("Clinical and Assay Data"),
            new StudyFolderTabs.SpecimensPage("Specimen Data"),
            new StudyFolderTabs.ManagePage("Manage")
        );

    StudyFolderType(StudyModule module, Set<Module> activeModules)
    {
        this(NAME,
                "Manage human and animal studies involving long-term observations at distributed sites. " +
                        "Use specimen repository for samples. Design and manage specialized assays. " +
                        "Analyze, visualize and share results.",
                module, activeModules);
    }

    StudyFolderType(String name, String description, StudyModule module, Set<Module> activeModules)
    {
        super(name, description, null,
                Arrays.asList(StudyModule.manageStudyPartFactory.createWebPart()),
                activeModules, module);
    }

    @NotNull
    @Override
    public Set<String> getLegacyNames()
    {
        return Collections.singleton("Study Redesign (CHAVI)");
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

    protected String getFolderTitle(ViewContext ctx)
    {
        Study study = StudyService.get().getStudy(ctx.getContainer());
        return study != null && study.getLabel() != null ? study.getLabel() : ctx.getContainer().getTitle();
    }

    public List<FolderTab> getDefaultTabs()
    {
        return PAGES;
    }
}
