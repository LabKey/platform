/*
 * Copyright (c) 2021-2026 LabKey Corporation
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

        if (SettingsManager.get().isSpecimenRequestEnabled(portalCtx.getContainer(), portalCtx.getUser()))
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
