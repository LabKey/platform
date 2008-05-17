/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.commons.lang.BooleanUtils;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.MenuButton;
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
public class CreateChartButton extends MenuButton
{
    private boolean _showCreateChart;
    private boolean _showCreateRReport;
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private final String _reportType;

    public CreateChartButton(String actionName, boolean showCreateChart, boolean showCreateRReport, String schemaName, String queryName, String viewName, String reportType)
    {
        super("Views");
        setActionName(actionName);

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

        Integer reportId = (Integer)ctx.get("reportId");

        if (_showCreateChart && reportId == null)
            addMenuItem("Create Chart", url);

        if (_showCreateRReport)
        {
            if (reportId == null)
            {
                if (RReport.isValidConfiguration())
                {
                    int perms = RReport.getEditPermissions();
                    if (ctx.getViewContext().hasPermission(perms))
                        addMenuItem("Create R View",  this.getActionName(ctx));
                    else
                        addMenuItem("Create R View", null, "alert('You do not have the required authorization to create R Views.')");
                }
                else
                    addMenuItem("Create R View", null, "alert('The R Program has not been configured properly, please request that an administrator configure R in the Admin Console.')");
            }

            if (RReport.isValidConfiguration())
            {
                try {
                    ViewContext context = ctx.getViewContext();
                    Report[] reports = ReportService.get().getReports(context.getUser(), context.getContainer(),
                            ChartUtil.getReportKey(_schemaName, _queryName));

                    if (reports.length > 0)
                    {
                        addMenuItem("Manage Views", new ActionURL("reports", "manageViews", ctx.getContainer()));
                    }
                }
                catch (Exception e)
                {
                    throw new RuntimeException("An error occurred retrieving the saved reports", e);
                }
            }
        }
        super.render(ctx, out);
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
