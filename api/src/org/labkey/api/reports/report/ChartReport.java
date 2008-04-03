package org.labkey.api.reports.report;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 3, 2006
 */
public abstract class ChartReport extends AbstractReport implements Report.ResultSetGenerator
{
    public String getDescriptorType()
    {
        return ChartReportDescriptor.TYPE;
    }

    public String getTypeDescription()
    {
        return "Chart View";
    }

    public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
    {
        return null;
    }

    public ResultSet generateResultSet(ViewContext context) throws Exception
    {
        return null;
    }
}
