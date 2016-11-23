/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.DefaultReportUIProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.GenericChartReport;
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
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        GenericChartReport.RenderType renderType = GenericChartReport.RenderType.BAR_PLOT;
        ActionURL url = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), settings, null);

        List<ReportService.DesignerInfo> info = new ArrayList<>();
        info.addAll(super.getDesignerInfo(context, settings));
        info.add(new DesignerInfoImpl(GenericChartReport.TYPE, "Chart", null, url,
                renderType.getIconPath(), ReportService.DesignerType.VISUALIZATION, renderType.getIconCls()));
        return info;
    }

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        GenericChartReport.RenderType renderType = GenericChartReport.RenderType.BAR_PLOT;
        ActionURL url = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), null, null);

        List<ReportService.DesignerInfo> designers = new ArrayList<>();
        designers.add(new DesignerInfoImpl(GenericChartReport.TYPE, "Chart", null, url,
                renderType.getIconPath(), ReportService.DesignerType.VISUALIZATION, renderType.getIconCls()));
        return designers;
    }

    public String getIconPath(Report report)
    {
        String type = report.getType();

        if (TimeChartReport.TYPE.equals(type))
            return "/visualization/report/timechart.gif";
        if (GenericChartReport.TYPE.equals(type))
        {
            GenericChartReport.RenderType renderType = ((GenericChartReport)report).getRenderType();

            if (renderType != null)
                return renderType.getIconPath();
        }
        return super.getIconPath(report);
    }

    public String getIconCls(Report report)
    {
        String type = report.getType();

        if (TimeChartReport.TYPE.equals(type))
            return "fa fa-line-chart";
        if (GenericChartReport.TYPE.equals(type))
        {
            GenericChartReport.RenderType renderType = ((GenericChartReport)report).getRenderType();

            if (renderType != null)
                return renderType.getIconCls();
        }

        return super.getIconCls(report);
    }
}
