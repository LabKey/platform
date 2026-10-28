/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.apache.commons.collections4.MultiSet;
import org.apache.commons.collections4.multiset.HashMultiSet;
import org.labkey.api.collections.MultiSetUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService.ReportMetricsVisitor;
import org.labkey.api.usageMetrics.UsageMetrics;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.VisualizationReportDescriptor;

public class ChartMetricsVisitor implements ReportMetricsVisitor
{
    private final MultiSet<GenericChartReport.RenderType> _chartCountsByRenderType = new HashMultiSet<>();
    private int _trendlineTypeCount = 0;
    private int _errorBarsCount = 0;

    @Override
    public void visit(Report report)
    {
        if (report instanceof GenericChartReport chart)
        {
            _chartCountsByRenderType.add(chart.getRenderType());
            if (chart.getDescriptor() instanceof VisualizationReportDescriptor descriptor)
            {
                String configJson = descriptor.getJSON();
                if (configJson.contains("\"trendlineType\":") && !configJson.contains("\"trendlineType\":\"\""))
                    _trendlineTypeCount++;
                if (configJson.contains("\"errorBars\":\"SD\"") || configJson.contains("\"errorBars\":\"SEM\""))
                    _errorBarsCount++;
            }
        }
    }

    @Override
    public void addMetrics(UsageMetrics metrics)
    {
        metrics
            .put("genericChartCountsByRenderType", MultiSetUtils.getOccurrenceMap(_chartCountsByRenderType), "Saved charts (GenericChartReport) by render type (BAR_PLOT, BOX_PLOT, etc.)")
            .put("genericChartWithTrendlineTypeCount", _trendlineTypeCount, "Saved charts with a trendline type set")
            .put("genericChartWithErrorBarsCount", _errorBarsCount, "Saved charts with SD or SEM error bars");
    }
}
