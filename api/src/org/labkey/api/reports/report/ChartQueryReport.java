/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.imagemap.ImageMapUtilities;
import org.jfree.chart.plot.Plot;
import org.labkey.api.data.*;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.chart.ChartRenderInfo;
import org.labkey.api.reports.chart.RenderInfo;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.RunChartReportView;
import org.labkey.api.reports.Report;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.view.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Karl Lum
 * Date: Mar 28, 2007
 */
public class ChartQueryReport extends ChartReport implements Report.ImageMapGenerator, Report.ImageReport
{
    public static final String TYPE = "ReportService.chartQueryReport";

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new RunChartReportView(this);
    }

    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (schemaName != null)
        {
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);

            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(context, dataRegionName, queryName);
                settings.setViewName(viewName);
                settings.setMaxRows(Table.ALL_ROWS);
                // need to reset the report id since we want to render the data grid, not the report
                settings.setReportId(null);

                ReportQueryView view = new ReportQueryView(schema, settings);
                final String filterParam = descriptor.getProperty("filterParam");
                if (!StringUtils.isEmpty(filterParam))
                {
                    final String filterValue = context.getActionURL().getParameter(filterParam);
                    if (filterValue != null)
                    {
                        view.setFilter(new SimpleFilter(filterParam, filterValue));
                    }
                }
                return view;
            }
        }
        return null;
    }

    public Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        ReportQueryView view = createQueryView(context, descriptor);
        if (view != null)
        {
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            RenderContext ctx = dataView.getRenderContext();
            return null == ctx.getResults() ? null : new ResultsImpl(ctx);
        }
        return null;
    }

    @Override
    public void renderImage(ViewContext context) throws IOException
    {
        List<String> errors = new ArrayList<>();
        JFreeChart chart = _createChart(context, errors, null);
        if (chart != null && errors.isEmpty())
        {
            final ChartReportDescriptor descriptor = (ChartReportDescriptor) getDescriptor();
            final BufferedImage img = chart.createBufferedImage(descriptor.getWidth(), descriptor.getHeight());
            HttpServletResponse response = context.getResponse();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);

            byte[] bytes = out.toByteArray();

            response.setContentType("image/png");
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            for (String error : errors)
            {
                sb.append(error);
                sb.append("\n");
            }
            ReportUtil.renderErrorImage(context.getResponse().getOutputStream(), this, sb.toString());
        }
    }

    public HttpView renderReport(ViewContext context)
    {
        ActionURL url = ReportUtil.getPlotChartURL(context, this);
        return new HtmlView("<img src='" + url.getLocalURIString() + "'>");
    }

    public String generateImageMap(ViewContext context, String id) throws Exception
    {
        return generateImageMap(context, id, null, new String[0]);
    }

    public String generateImageMap(ViewContext context, String id, String imageMapCallback) throws Exception
    {
        return generateImageMap(context, id, imageMapCallback, new String[0]);
    }

    public String generateImageMap(ViewContext context, String id, String imageMapCallback, String[] callbackParams) throws Exception
    {
        ChartRenderInfo renderInfo = new RenderInfo(imageMapCallback, callbackParams);

        List<String> errors = new ArrayList<>();
        JFreeChart chart = _createChart(context, errors, renderInfo);
        if (chart != null && errors.isEmpty())
        {
            final ChartReportDescriptor descriptor = (ChartReportDescriptor) getDescriptor();
            ChartRenderingInfo info = new ChartRenderingInfo();
            chart.createBufferedImage(descriptor.getWidth(), descriptor.getHeight(), info);

            return ImageMapUtilities.getImageMap(id, info);
        }
        return null;
    }

    private JFreeChart _createChart(ViewContext context, List<String> errors, ChartRenderInfo info)
    {
        final ReportDescriptor reportDescriptor = getDescriptor();
        if (reportDescriptor instanceof ChartReportDescriptor)
        {
            try
            {
                ChartReportDescriptor descriptor = (ChartReportDescriptor) reportDescriptor;
                ReportQueryView view = createQueryView(context, descriptor);
                if (view != null)
                {
                    ChartRenderer renderer = ChartRendererFactory.get().getChartRenderer(descriptor.getProperty(ChartReportDescriptor.Prop.chartType));
                    if (renderer != null)
                    {
                        renderer.setRenderInfo(info);
                        Plot plot = renderer.createPlot(descriptor, view);

                        JFreeChart chart = new JFreeChart(plot);
                        chart.setTitle(descriptor.getReportName());

                        return chart;
                    }
                    else
                        errors.add("A renderer could not be found for this chart type");
                }
                else
                {
                    errors.add("Unable to render report, a ReportQueryView could not be created.");
                }
            }
            catch (Exception e)
            {
                errors.add("An error occurred rendering this chart, you may not have permission to view it");
            }
        }
        else
        {
            errors.add("Invalid report params: The ReportDescriptor must be an instance of ChartReportDescriptor");
        }
        return null;
    }

    public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
    {
        return new ChartLabelGenerator();
    }

    private class ChartLabelGenerator implements ChartReportDescriptor.LegendItemLabelGenerator
    {
        private Map<String, String> _columnMap;

        public String generateLabel(ViewContext context, ReportDescriptor descriptor, String itemName) throws Exception
        {
            if (_columnMap == null)
            {
                _columnMap = new CaseInsensitiveHashMap<>();
                ReportQueryView view = createQueryView(context, descriptor);
                if (view != null)
                {
                    for (DisplayColumn column : view.getDisplayColumns())
                    {
                        final ColumnInfo info = column.getColumnInfo();
                        if (info != null)
                            _columnMap.put(info.getAlias(), info.getLabel());
                    }
                }
            }

            if (_columnMap.containsKey(itemName))
                return _columnMap.get(itemName);
            return itemName;
        }
    }
}
