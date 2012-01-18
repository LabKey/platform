package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
