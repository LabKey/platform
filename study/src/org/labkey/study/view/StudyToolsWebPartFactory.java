package org.labkey.study.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.study.StudyFolderTabs;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.study.view.StudyToolsWebPart;
import org.labkey.api.study.view.ToolsWebPartFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.visualization.VisualizationUrls;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;

import java.util.ArrayList;
import java.util.List;

public class StudyToolsWebPartFactory extends ToolsWebPartFactory
{
    public StudyToolsWebPartFactory()
    {
        super(StudyService.STUDY_TOOLS_WEBPART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
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
            items.add(new StudyToolsWebPart.Item("Settings", iconBase + "settings.png", new ActionURL(StudyController.ManageStudyAction.class, portalCtx.getContainer())));
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
                if (StringUtils.equalsIgnoreCase(folderTab.getName(), StudyFolderTabs.ParticipantsPage.PAGE_ID))
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
        return StudyService.STUDY_TOOLS_WEBPART_NAME;
    }
}
