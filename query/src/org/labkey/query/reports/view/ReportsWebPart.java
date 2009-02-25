/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.query.reports.view;

import org.labkey.api.view.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.query.QueryParam;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

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
        Report report = getReport(properties);

        boolean showTabs = BooleanUtils.toBoolean(properties.get(Report.renderParam.showTabs.name()));
        getViewContext().put(Report.renderParam.reportWebPart.name(), "true");

        if (properties.containsKey(Report.renderParam.showSection.name()))
            getViewContext().put(Report.renderParam.showSection.name(), properties.get(Report.renderParam.showSection.name()));

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
        include(new HtmlView("Unable to display the specified report."));        
    }

    private Report getReport(Map<String, String> props) throws Exception
    {
        String reportIdString = props.get(Report.renderParam.reportId.name());
        if (reportIdString != null)
        {
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(reportIdString);

            //allow bare report ids for backward compatibility
            if (reportId == null)
                reportId = new DbReportIdentifier(Integer.parseInt(reportIdString));

            if (reportId != null)
                return reportId.getReport();
        }
        else
        {
            // try schema/query/reportName combo

            String reportName = props.get(Report.renderParam.reportName.name());
            if (!StringUtils.isEmpty(reportName))
            {
                String key = ReportUtil.getReportKey(props.get(QueryParam.schemaName.name()), props.get(QueryParam.queryName.name()));

                for (Report rpt : ReportService.get().getReports(getViewContext().getUser(), getViewContext().getContainer(), key))
                {
                    if (reportName.equals(rpt.getDescriptor().getReportName()))
                        return rpt;
                }
            }
        }
        return null;
    }
}
