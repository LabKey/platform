/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
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
        List<ReportService.DesignerInfo> info = new ArrayList<>();
        info.addAll(super.getDesignerInfo(context, settings));
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);
        if ("study".equalsIgnoreCase(settings.getSchemaName()))
        {
            ActionURL designerURL = urlProvider.getTimeChartDesignerURL(context.getContainer(), context.getUser(), settings);
            info.add(new DesignerInfoImpl(TimeChartReport.TYPE, "Time Chart", null, designerURL,
                    "/visualization/report/timechart.gif", ReportService.DesignerType.VISUALIZATION));
        }

        GenericChartReport.RenderType boxType = GenericChartReport.RenderType.BOX_PLOT;

        ActionURL boxPlotURL = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), settings, boxType);
        info.add(new DesignerInfoImpl(GenericChartReport.TYPE, boxType.getName(), null, boxPlotURL,
                boxType.getIconPath(), ReportService.DesignerType.VISUALIZATION));

        GenericChartReport.RenderType scatterType = GenericChartReport.RenderType.SCATTER_PLOT;

        ActionURL scatterPlotURL = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), settings, scatterType);
        info.add(new DesignerInfoImpl(GenericChartReport.TYPE, scatterType.getName(), null, scatterPlotURL,
                scatterType.getIconPath(), ReportService.DesignerType.VISUALIZATION));

        return info;
    }

    /**
     * Add report creation to UI's that aren't associated with a query (manage views, data views)
     */
    @Override
    public List<ReportService.DesignerInfo> getDesignerInfo(ViewContext context)
    {
        List<ReportService.DesignerInfo> designers = new ArrayList<>();
        Study study = StudyService.get().getStudy(context.getContainer());
        VisualizationUrls urlProvider = PageFlowUtil.urlProvider(VisualizationUrls.class);

        if (study != null)
        {
            ActionURL designerURL = urlProvider.getTimeChartDesignerURL(context.getContainer());

            DesignerInfoImpl info = new DesignerInfoImpl(TimeChartReport.TYPE, "Time Chart", null, designerURL,
                    "/visualization/report/timechart.gif", ReportService.DesignerType.VISUALIZATION);
            info.setId("create_timeChart");
            designers.add(info);
        }

        GenericChartReport.RenderType boxType = GenericChartReport.RenderType.BOX_PLOT;

        ActionURL boxPlotURL = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), null, boxType);
        designers.add(new DesignerInfoImpl(GenericChartReport.TYPE, boxType.getName(), null, boxPlotURL,
                boxType.getIconPath(), ReportService.DesignerType.VISUALIZATION));

        GenericChartReport.RenderType scatterType = GenericChartReport.RenderType.SCATTER_PLOT;

        ActionURL scatterPlotURL = urlProvider.getGenericChartDesignerURL(context.getContainer(), context.getUser(), null, scatterType);
        designers.add(new DesignerInfoImpl(GenericChartReport.TYPE, scatterType.getName(), null, scatterPlotURL,
                scatterType.getIconPath(), ReportService.DesignerType.VISUALIZATION));

        return designers;
    }

    public String getIconPath(Report report)
    {
        String contextPath = AppProps.getInstance().getContextPath();
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
}
