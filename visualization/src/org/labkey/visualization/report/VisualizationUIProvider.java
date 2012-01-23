/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.visualization.report;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.TimeChartReport;
import org.labkey.api.visualization.VisualizationUrls;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Jan 28, 2011 5:02:16 PM
 */
public class VisualizationUIProvider extends DefaultReportUIProvider
{
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings)
    {
        List<ReportService.DesignerInfo> info = new ArrayList<ReportService.DesignerInfo>();
        info.addAll(super.getDesignerInfo(context, settings));
        if ("study".equalsIgnoreCase(settings.getSchemaName()))
        {
            VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
            ActionURL designerURL = urlProvider.getTimeChartDesignerURL(context.getContainer(), context.getUser(), settings);
            info.add(new DesignerInfoImpl(TimeChartReport.TYPE, "Time Chart", designerURL));
        }
        return info;
    }

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<ReportService.DesignerInfo>();
        Study study = StudyService.get().getStudy(context.getContainer());

        if (study != null)
        {
            VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
            ActionURL designerURL = urlProvider.getTimeChartDesignerURL(context.getContainer());

            DesignerInfoImpl info = new DesignerInfoImpl(TimeChartReport.TYPE, "Time Chart", designerURL);
            info.setId("create_timeChart");
            designers.add(info);
        }
        return designers;
    }

    @Override
    public String getReportIcon(ViewContext context, String reportType)
    {
        if (TimeChartReport.TYPE.equals(reportType))
            return context.getContextPath() + "/visualization/report/timechart.gif";
        return super.getReportIcon(context, reportType);
    }
}
