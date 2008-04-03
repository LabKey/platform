package org.labkey.api.reports.chart;

import org.jfree.chart.plot.Plot;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Sep 27, 2006
 */
public interface ChartRenderer
{
    public String getType();
    public String getName();
    public Plot createPlot(ChartReportDescriptor descriptor, ReportQueryView view) throws Exception;
    public Map<String, String> getDisplayColumns(QueryView view, boolean isXAxis);

    /**
     * specify additional rendering info
     */
    public void setRenderInfo(ChartRenderInfo info);
    public ChartRenderInfo getRenderInfo();
}
