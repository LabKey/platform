package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.reports.ReportsController;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 17, 2012
 */
public class ParticipantReport extends AbstractReport
{
    public static final String TYPE = "ReportService.ParticipantReport";

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

        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (getDescriptor().canEdit(context.getUser(), context.getContainer()))
        {
            NavTree customize = new NavTree("");
            customize.setScript("customizeParticipantReport('" + form.getComponentId() + "');");
            view.setCustomize(customize);

            NavTree menu = new NavTree();
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }
}
