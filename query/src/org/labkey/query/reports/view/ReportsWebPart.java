package org.labkey.query.reports.view;

import org.labkey.api.view.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.Report;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.BooleanUtils;

import java.util.Map;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPart extends WebPartView
{
    Portal.WebPart _webPart;

    public ReportsWebPart(ViewContext context, Portal.WebPart part)
    {
        setFrame(FrameType.PORTAL);

        _webPart = part;
        Map<String, String> properties = part.getPropertyMap();
        String title = properties.get("title");
        if (title == null)
            title = "Reports";

        setTitle(title);
    }

    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        Map<String, String> properties = _webPart.getPropertyMap();
        String reportId = properties.get("reportId");
        boolean showTabs = BooleanUtils.toBoolean(properties.get("showTabs"));
        getViewContext().put("reportWebPart", "true");
        if (reportId != null)
        {
            Report report = ReportService.get().getReport( NumberUtils.toInt(reportId));

            if (report != null)
            {
                HttpView view = showTabs ?
                        report.getRunReportView(getViewContext()) :
                        report.renderReport(getViewContext());
                if (view != null)
                {
                    include(view);
                    return;
                }
            }
        }
        include(new HtmlView("Unable to display the specified report."));        
    }
}
