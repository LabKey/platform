package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.util.VirtualFile;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:39:43 PM
 */
public class ReportWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        fs.makeDir("reports");
        VirtualFile reports = fs.getDir("reports");

        for (Report report : ReportService.get().getReports(ctx.getUser(), ctx.getContainer()))
        {
            report.serializeToFolder(reports);
        }
    }
}
