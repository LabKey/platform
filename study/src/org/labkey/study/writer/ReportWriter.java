package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:39:43 PM
 */
public class ReportWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx) throws Exception
    {
        for (Report report : ReportService.get().getReports(ctx.getUser(), study.getContainer()))
        {
            System.out.println("I'm exporting report " + report.getDescriptor().getReportName());
        }
    }
}
