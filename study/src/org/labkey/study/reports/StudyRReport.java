package org.labkey.study.reports;

import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jul 25, 2007
 */
public class StudyRReport extends RReport
{
    public static final String TYPE = "Study.rReport";

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());

        return ReportQueryViewFactory.get().generateQueryView(context, descriptor, queryName, viewName);
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new StudyRunRReportView(this);
    }
}
