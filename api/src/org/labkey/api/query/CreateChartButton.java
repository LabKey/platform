package org.labkey.api.query;

import org.apache.commons.lang.BooleanUtils;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 28, 2007
 */
public class CreateChartButton extends ActionButton
{
    private boolean _showCreateChart;
    private boolean _showCreateRReport;
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private final String _reportType;

    public CreateChartButton(String actionName, boolean showCreateChart, boolean showCreateRReport, String schemaName, String queryName, String viewName, String reportType)
    {
        super(actionName, "Views", DataRegion.MODE_GRID, ActionButton.Action.LINK);

        _showCreateChart = showCreateChart;
        _showCreateRReport = showCreateRReport;

        _schemaName = schemaName;
        _queryName = queryName;
        _viewName = viewName;
        _reportType = reportType;
    }

    public void render(RenderContext ctx, Writer out) throws IOException {
        if (!shouldRender(ctx))
            return;

        ChartDesignerBean chartBean = new ChartDesignerBean();

        chartBean.setReportType(ChartQueryReport.TYPE);
        chartBean.setSchemaName(_schemaName);
        chartBean.setQueryName(_queryName);
        chartBean.setViewName(_viewName);

        ActionURL url = ChartUtil.getChartDesignerURL(HttpView.currentContext(), chartBean);

        out.write("<a href='javascript:void(0)' onclick=\"showMenu(this, 'reportMenu');\">");
        out.write("<img border=0 src='" + PageFlowUtil.buttonSrc(getCaption(ctx), "shadedMenu") + "'>");
        out.write("</a>");

        if (!BooleanUtils.toBoolean((String)ctx.get("RReportMenuRendered")))
        {
            ctx.put("RReportMenuRendered", "true");
            Integer reportId = (Integer)ctx.get("reportId");

            out.write("<div style=\"display:none\" id=\"reportMenu\" class=\"yuimenu\">");
            out.write("<div class=\"bd\">");
            out.write("<ul class=\"first-of-type\">");
            if (_showCreateChart && reportId == null)
                out.write("<li class=\"yuimenuitem\"><a href=\"" + url + "\">Create Chart</a></li>");

            if (_showCreateRReport)
            {
                if (reportId == null)
                {
                    if (RReport.isValidConfiguration())
                    {
                        int perms = RReport.getEditPermissions();
                        if (ctx.getViewContext().hasPermission(perms))
                            out.write("<li class=\"yuimenuitem\"><a href=\"" + this.getActionName(ctx) + "\">Create R View</a></li>");
                        else
                            out.write("<li class=\"yuimenuitem\"><a href=\"javascript:alert('You do not have the required authorization to create R Views.')\">Create R View</a></li>");
                    }
                    else
                        out.write("<li class=\"yuimenuitem\"><a href=\"javascript:alert('The R Program has not been configured properly, please request that an administrator configure R in the Admin Console.')\">Create R View</a></li>");

                    //out.write("</ul><ul>");
                }

                if (RReport.isValidConfiguration())
                {
                    try {
                        ViewContext context = ctx.getViewContext();
                        Report[] reports = ReportService.get().getReports(context.getUser(), context.getContainer(),
                                ChartUtil.getReportKey(_schemaName, _queryName));

                        if (reports.length > 0)
                        {
                            out.write("<li class=\"yuimenuitem\"><a href=\"" + new ActionURL("reports", "manageViews", ctx.getContainer()) + "\">Manage Views</a></li>");
                        }
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException("An error occurred retrieving the saved reports", e);
                    }
                }
            }
            out.write("</ul></div></div>");
        }
    }

    protected String getShowReportMenuItem(RenderContext context, Report report)
    {
        ActionURL url = report.getRunReportURL(context.getViewContext());
        if (url != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<li class=\"yuimenuitem\">");
            sb.append("<a href='");
            sb.append(url);
            sb.append("'>");
            sb.append(PageFlowUtil.filter(report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName)));
            sb.append("</a></li>");

            return sb.toString();
        }
        return null;
    }
}
