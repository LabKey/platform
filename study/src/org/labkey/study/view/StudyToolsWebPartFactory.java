/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.study.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.study.StudyFolderTabs;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.VisualizationUrls;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.specimen.ShowSearchAction;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.security.permissions.ManageStudyPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Oct 1, 2011 1:49:24 PM
 */
public abstract class StudyToolsWebPartFactory extends BaseWebPartFactory
{
    public StudyToolsWebPartFactory(String name, @NotNull String defaultLocation, String... additionalLocations)
    {
        super(name, defaultLocation, additionalLocations);
    }

    public static class Specimens extends StudyToolsWebPartFactory
    {
        public Specimens()
        {
            super(StudyService.SPECIMEN_TOOLS_WEBPART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        }

        @Override
        protected List<StudyToolsWebPart.Item> getItems(ViewContext portalCtx)
        {
            String iconBase = portalCtx.getContextPath() + "/study/tools/";
            List<StudyToolsWebPart.Item> items = new ArrayList<>();

            ActionURL vialSearchURL = new ActionURL(ShowSearchAction.class, portalCtx.getContainer());
            vialSearchURL.addParameter("showVials", true);
            items.add(new StudyToolsWebPart.Item("Vial Search", iconBase + "specimen_search.png", vialSearchURL));

            if (SpecimenManager.getInstance().isSampleRequestEnabled(portalCtx.getContainer()))
            {
                if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), RequestSpecimensPermission.class))
                    items.add(new StudyToolsWebPart.Item("New Request", iconBase + "specimen_request.png", new ActionURL(SpecimenController.ShowCreateSampleRequestAction.class, portalCtx.getContainer() )));
            }
            items.add(new StudyToolsWebPart.Item("Specimen Reports", iconBase + "specimen_report.png", new ActionURL(SpecimenController.AutoReportListAction.class, portalCtx.getContainer() )));

            if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
                items.add(new StudyToolsWebPart.Item("Settings", iconBase + "settings.png", new ActionURL(StudyController.ManageStudyAction.class, portalCtx.getContainer() )));

            return items;
        }

        @Override
        protected String getTitle()
        {
            return StudyService.SPECIMEN_TOOLS_WEBPART_NAME;
        }
    }

    public static class Data extends StudyToolsWebPartFactory
    {
        public Data()
        {
            super(StudyService.DATA_TOOLS_WEBPART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        }

        @Override
        protected List<StudyToolsWebPart.Item> getItems(ViewContext portalCtx)
        {
            String iconBase = portalCtx.getContextPath() + "/study/tools/";
            List<StudyToolsWebPart.Item> items = new ArrayList<>();

            VisualizationUrls visUrlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
            if (visUrlProvider != null)
            {
                URLHelper timeChartURL = visUrlProvider.getTimeChartDesignerURL(portalCtx.getContainer());
                items.add(new StudyToolsWebPart.Item("New Time Chart", iconBase + "timeline_chart.png", timeChartURL));
            }

            String noun = StudyService.get().getSubjectNounSingular(portalCtx.getContainer());

            items.add(new StudyToolsWebPart.Item("New " + noun + " Report", iconBase + "participant_report.png", new ActionURL(ReportsController.ParticipantReportAction.class, portalCtx.getContainer())));
            items.add(getParticipantListItem(portalCtx, noun, iconBase));
            
            items.add(new StudyToolsWebPart.Item("Study Navigator", iconBase + "study_overview.png", new ActionURL(StudyController.OverviewAction.class, portalCtx.getContainer())));

            if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
                items.add(new StudyToolsWebPart.Item("Settings", iconBase + "settings.png", new ActionURL(StudyController.ManageStudyAction.class, portalCtx.getContainer() )));
            return items;
        }

        private StudyToolsWebPart.Item getParticipantListItem(ViewContext context, String noun, String iconBase)
        {
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(context.getContainer()));
            Portal.PortalPage participantPage = pages.get(StudyFolderTabs.ParticipantsPage.PAGE_ID);
            if (null != participantPage && !participantPage.isHidden())
            {
                for (FolderTab folderTab : context.getContainer().getFolderType().getDefaultTabs())
                {
                    if (StringUtils.equalsIgnoreCase(folderTab.getName(),StudyFolderTabs.ParticipantsPage.PAGE_ID))
                    {
                        ActionURL url = folderTab.getURL(context.getContainer(), context.getUser());
                        return new StudyToolsWebPart.Item(noun + " List", iconBase + "participant_list.png", url);
                    }
                }
            }
            // the participants tab isn't visible, just show the web part in the existing tab
            return new StudyToolsWebPart.Item(noun + " List", iconBase + "participant_list.png", new ActionURL(StudyController.SubjectListAction.class, context.getContainer()));
        }

        @Override
        protected String getTitle()
        {
            return StudyService.DATA_TOOLS_WEBPART_NAME;
        }
    }

    protected abstract List<StudyToolsWebPart.Item> getItems(ViewContext portalCtx);

    protected abstract String getTitle();

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        return new StudyToolsWebPart(getTitle(), webPart.getLocation().equals(HttpView.BODY), getItems(portalCtx));
    }
}
