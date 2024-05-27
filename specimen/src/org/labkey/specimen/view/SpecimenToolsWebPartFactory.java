package org.labkey.specimen.view;

import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.study.view.StudyToolsWebPart;
import org.labkey.api.study.view.ToolsWebPartFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.specimen.actions.ShowSearchAction;
import org.labkey.specimen.actions.SpecimenController.AutoReportListAction;
import org.labkey.specimen.actions.SpecimenController.ShowCreateSpecimenRequestAction;
import org.labkey.specimen.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.specimen.SpecimensPage.SPECIMEN_TOOLS_WEBPART_NAME;

public class SpecimenToolsWebPartFactory extends ToolsWebPartFactory
{
//    public static final String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";  TODO: Remove from SpecimenPage and uncomment

    public SpecimenToolsWebPartFactory()
    {
        super(SPECIMEN_TOOLS_WEBPART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
    }

    @Override
    protected List<StudyToolsWebPart.Item> getItems(ViewContext portalCtx)
    {
        String iconBase = portalCtx.getContextPath() + "/study/tools/";
        List<StudyToolsWebPart.Item> items = new ArrayList<>();

        ActionURL vialSearchURL = ShowSearchAction.getShowSearchURL(portalCtx.getContainer(), true);
        items.add(new StudyToolsWebPart.Item("Vial Search", iconBase + "specimen_search.png", vialSearchURL));

        if (SettingsManager.get().isSpecimenRequestEnabled(portalCtx.getContainer()))
        {
            if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), RequestSpecimensPermission.class))
                items.add(new StudyToolsWebPart.Item("New Request", iconBase + "specimen_request.png", new ActionURL(ShowCreateSpecimenRequestAction.class, portalCtx.getContainer())));
        }
        items.add(new StudyToolsWebPart.Item("Specimen Reports", iconBase + "specimen_report.png", new ActionURL(AutoReportListAction.class, portalCtx.getContainer())));

        if (portalCtx.getContainer().hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
            items.add(new StudyToolsWebPart.Item("Settings", iconBase + "settings.png", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(portalCtx.getContainer())));

        return items;
    }

    @Override
    protected String getTitle()
    {
        return SPECIMEN_TOOLS_WEBPART_NAME;
    }
}
