package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public abstract class RunReportView extends TabStripView
{
    public static final String CACHE_PARAM = "cacheKey";
    public static final String MSG_PARAM = "msg";

    public static final String TAB_VIEW = "View";
    public static final String TAB_DATA = "Data";

    protected BindException _errors;

    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        final Report report = getReport();

        if (report == null)
        {
            include(new HtmlView("Unable to find the specified report"));
            return;
        }
        renderTitle(model, out);
        if (getViewContext().getActionURL().getParameter(MSG_PARAM) != null)
            getErrors().reject(MSG_PARAM, getViewContext().getActionURL().getParameter(MSG_PARAM));

        super.renderInternal(model, out);
    }

    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        final Report report = getReport();

        if (report.getDescriptor().getReportName() != null)
        {
            final StringBuffer sb = new StringBuffer();

            sb.append("<table><tr>");
            sb.append("<td style=\"border:none;font-weight:bold\">View :</td><td style=\"border:none\">");
            sb.append(report.getDescriptor().getReportName());
            if (report.getDescriptor().getReportDescription() != null)
            {
                sb.append("&nbsp;(");
                sb.append(report.getDescriptor().getReportDescription());
                sb.append(")");
            }
            sb.append("</td></tr>");
            String viewName = report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName);
            if (viewName != null)
            {
                sb.append("<tr><td style=\"border:none;font-weight:bold\">Created from Grid View :</td><td style=\"border:none\">");
                sb.append(viewName);
                sb.append("</td></tr>");
            }
            if (isReportInherited(report))
            {
                sb.append("<tr><td style=\"border:none;font-weight:bold\">Inherited from project :</td><td style=\"border:none\">");
                sb.append(report.getDescriptor().getContainerPath());
                sb.append("</td></tr>");
            }
            sb.append("</table>");
            include(new HttpView() {

                protected void renderInternal(Object model, PrintWriter out) throws Exception {
                    out.write("<table width=\"100%\"><tr class=\"wpHeader\"><td align=\"left\">" + sb + "</td></tr><tr><td></td>&nbsp;</tr></table>");
                }
            });
        }
    }

    protected boolean isReportInherited(Report report)
    {
        return ChartUtil.isReportInherited(getViewContext().getContainer(), report);
    }

    public BindException getErrors()
    {
        if (_errors == null)
            _errors = new BindException(this, "form");

        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    protected abstract Report getReport() throws Exception;

    protected static class ReportTabInfo extends TabInfo
    {
        public ReportTabInfo(String name, String id, ActionURL url)
        {
            super(name, id, url.deleteParameter(MSG_PARAM));
        }
    }
}
