/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.studydesign.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.security.permissions.ManageStudyPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

/**
 * User: cnathe
 * Date: 12/16/13
 */
public class AssayScheduleWebpartFactory extends StudyDesignWebpartFactory
{
    public static String NAME = "Assay Schedule";

    public AssayScheduleWebpartFactory()
    {
        super(NAME);
    }

    @Override
    public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        if (!canShow(portalCtx.getContainer()))
            return null;

        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/studydesign/view/assayScheduleWebpart.jsp", webPart);
        view.setTitle(NAME);
        view.setFrame(WebPartView.FrameType.PORTAL);

        Container c = portalCtx.getContainer();
        Study study = StudyService.get().getStudy(c);
        if (c.hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
        {
            String timepointMenuName;
            if (study != null && study.getTimepointType() == TimepointType.DATE)
                timepointMenuName = "Manage Timepoints";
            else
                timepointMenuName = "Manage Visits";

            NavTree menu = new NavTree();
            menu.addChild(timepointMenuName, PageFlowUtil.urlProvider(StudyUrls.class).getManageVisitsURL(c));
            view.setNavMenu(menu);
        }

        return view;
    }
}
