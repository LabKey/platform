/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.reports.ReportsController;

import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 17, 2012
 */
public class ParticipantReport extends AbstractReport
{
    public static final String TYPE = "ReportService.ParticipantReport";
    public static final String MEASURES_PROP = "measures";
    public static final String GROUPS_PROP = "groups";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Participant Report";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        ReportsController.ParticipantReportForm form = new ReportsController.ParticipantReportForm();
        
        form.setReportId(getReportId());
        form.setComponentId("participant-report-panel-" + UniqueID.getRequestScopedUID(context.getRequest()));

        JspView<ReportsController.ParticipantReportForm> view = new JspView<ReportsController.ParticipantReportForm>("/org/labkey/study/view/participantReport.jsp", form);

        form.setExpanded(!(context.get("reportWebPart") != null));
        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (canEdit(context.getUser(), context.getContainer()))
        {
            String script = String.format("javascript:customizeParticipantReport('%s');", form.getComponentId());
            NavTree edit = new NavTree("Edit", script, AppProps.getInstance().getContextPath() + "/_images/partedit.png");
            view.addCustomMenu(edit);

            NavTree menu = new NavTree();
            menu.addChild("New " + StudyService.get().getSubjectNounSingular(context.getContainer()) + " Report", new ActionURL(ReportsController.ParticipantReportAction.class, context.getContainer()));
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = ParticipantReport.class.getResourceAsStream("participantThumbnail.gif");
        return new Thumbnail(is, "image/gif");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:ParticipantReportStatic";
    }
}
