package org.labkey.study.reports;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

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
        JspView participantReport = new JspView<Report>("/org/labkey/study/view/participantReport.jsp", this);

        participantReport.setFrame(WebPartView.FrameType.NONE);
        return new HBox(participantReport);
    }
}
