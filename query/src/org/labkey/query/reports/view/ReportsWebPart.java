/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.view.RenderBackgroundRReportView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.*;

import java.io.PrintWriter;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPart extends WebPartView
{
    Portal.WebPart _webPart;
    Report _report;

    public ReportsWebPart(ViewContext context, Portal.WebPart part)
    {
        super(FrameType.PORTAL);

        _webPart = part;
        Map<String, String> properties = part.getPropertyMap();

        String title = StringUtils.defaultString(properties.get("title"), "Reports");
        setTitle(title);

        try
        {
            _report = getReport(properties);
            if (null != _report)
            {
                setTitleHref(_report.getRunReportURL(context));
                addClientDependencies(_report.getDescriptor().getClientDependencies());
            }
        }
        catch (Exception x)
        {
        }
    }


    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        Map<String, String> properties = _webPart.getPropertyMap();

        if (null == _report)
            _report = getReport(properties);

        boolean showTabs = BooleanUtils.toBoolean(properties.get(Report.renderParam.showTabs.name()));
        getViewContext().put(Report.renderParam.reportWebPart.name(), "true");

        if (properties.containsKey(Report.renderParam.showSection.name()))
            getViewContext().put(Report.renderParam.showSection.name(), properties.get(Report.renderParam.showSection.name()));

        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
        {
            if (properties.containsKey(Report.renderParam.reportSessionId.name()))
                getViewContext().put(Report.renderParam.reportSessionId.name(), properties.get(Report.renderParam.reportSessionId.name()));
        }

        if (_report != null)
        {
            HttpView view;

            if (showTabs)
            {
                view = _report.getRunReportView(getViewContext());
            }
            else
            {
                ReportDescriptor descriptor = _report.getDescriptor();
                boolean runInBackground = BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.runInBackground));

                // for now, limit pipeline view to saved R reports
                if (runInBackground && _report instanceof RReport)
                    view = new RenderBackgroundRReportView((RReport)_report);
                else
                    view = _report.renderReport(getViewContext());
            }

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
        Report report = null;

        String reportIdString = props.get(Report.renderParam.reportId.name());
        if (reportIdString != null)
        {
            report = ReportUtil.getReportById(getViewContext(), reportIdString);
        }
        else
        {
            // try schema/query/reportName combo
            String reportName = props.get(Report.renderParam.reportName.name());
            if (!StringUtils.isEmpty(reportName))
            {
                String key = StringUtils.trimToNull(ReportUtil.getReportKey(props.get(QueryParam.schemaName.name()), props.get(QueryParam.queryName.name())));
                report = ReportUtil.getReportByName(getViewContext(), reportName, key);
            }
        }
        return report;
    }
}
