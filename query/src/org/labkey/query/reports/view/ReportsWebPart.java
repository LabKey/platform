/*
 * Copyright (c) 2008 LabKey Corporation
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
        ReportIdentifier reportId = null;
        String reportIdString = properties.get(Report.renderParam.reportId.name());
        if(null != reportIdString)
        {
            reportId = ReportService.get().getReportIdentifier(reportIdString);

            //allow bare report ids for backward compatibility 
            if(null == reportId)
                reportId = new DbReportIdentifier(Integer.parseInt(reportIdString));
        }

        boolean showTabs = BooleanUtils.toBoolean(properties.get(Report.renderParam.showTabs.name()));
        getViewContext().put(Report.renderParam.reportWebPart.name(), "true");

        if (properties.containsKey(Report.renderParam.showSection.name()))
            getViewContext().put(Report.renderParam.showSection.name(), properties.get(Report.renderParam.showSection.name()));

        if (reportId != null)
        {
            Report report = reportId.getReport();

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
