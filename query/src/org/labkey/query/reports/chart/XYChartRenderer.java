/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.query.reports.chart;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.chart.ChartRenderInfo;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Sep 27, 2006
 */
public class XYChartRenderer extends AbstractChartRenderer implements ChartRenderer
{
    private static final XYChartRenderer _instance = new XYChartRenderer();

    public static XYChartRenderer getInstance()
    {
        return _instance;
    }

    private XYChartRenderer(){}

    public String getType()
    {
        return "1";
    }

    public String getName()
    {
        return "XY Scatter Plot";
    }

    public Plot createPlot(ChartReportDescriptor descriptor, ReportQueryView view) throws Exception
    {
        ResultSet rs = generateResultSet(view);
        if (rs != null)
        {
            try
            {
                Map<String, String> labels = getLabelMap(view);
                Map<String, XYSeries> datasets = new HashMap<>();
                for (String columnName : descriptor.getColumnYName())
                {
                    if (!StringUtils.isEmpty(columnName))
                        datasets.put(columnName, new XYSeries(getLabel(labels, columnName)));
                }

                String columnX = descriptor.getProperty(ChartReportDescriptor.Prop.columnXName);
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                // create a jfreechart dataset
                while (rs.next())
                {
                    Map<String, Object> rowMap = factory.getRowMap(rs);

                    for (Map.Entry<String, XYSeries> series : datasets.entrySet())
                    {
                        addDataItem(series.getValue(), rowMap, columnX, series.getKey());
                        //addDataItem(series.getValue(), rowMap.get(columnX), rowMap.get(series.getKey()));
                    }
                }
                boolean isMultiYAxis = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleYAxis));
                boolean showLines = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showLines));
                boolean showMultiPlot = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleCharts));

                XYPlot plot = new XYPlot();
                XYSeriesCollection collection = new XYSeriesCollection();

                for (XYSeries series : datasets.values())
                    collection.addSeries(series);

                if (showMultiPlot && datasets.size() > 1)
                {
                    boolean isVertical = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.isVerticalOrientation));
                    plot = isVertical ?
                            new CombinedDomainXYPlot((ValueAxis)createAxis(X_AXIS, descriptor, null)) : 
                            new CombinedRangeXYPlot((ValueAxis)createAxis(Y_AXIS, descriptor, null));

                    for (XYSeries series : datasets.values())
                    {
                        XYPlot subPlot = new XYPlot();

                        subPlot.setDataset(new XYSeriesCollection(series));
                        subPlot.setRangeAxis((ValueAxis)createAxis(Y_AXIS, descriptor, series.getKey().toString()));
                        subPlot.setDomainAxis((ValueAxis)createAxis(X_AXIS, descriptor, getLabel(labels, columnX)));
                        subPlot.setRenderer(new XYLineAndShapeRenderer(showLines, true));
                        subPlot.getRenderer().setURLGenerator(new XYUrlGenerator(getRenderInfo()));
                        subPlot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        if (isVertical)
                            ((CombinedDomainXYPlot)plot).add(subPlot);
                        else
                            ((CombinedRangeXYPlot)plot).add(subPlot);
                    }
                }
                else if (isMultiYAxis && collection.getSeriesCount() > 0)
                {
                    int idx = 0;
                    for (Object series : collection.getSeries())
                    {
                        XYSeries xySeries = (XYSeries)series;
                        plot.setDataset(idx, new XYSeriesCollection(xySeries));

                        Axis axis = createAxis(Y_AXIS, descriptor, xySeries.getKey().toString());
                        plot.setRangeAxis(idx, (ValueAxis)axis);
                        plot.setRenderer(idx, new XYLineAndShapeRenderer(showLines, true));
                        plot.getRenderer().setURLGenerator(new XYUrlGenerator(getRenderInfo()));
                        plot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        plot.mapDatasetToRangeAxis(idx, idx);
                        idx++;
                    }
                }
                else
                {
                    Axis axis = createAxis(Y_AXIS, descriptor, "");
                    if (collection.getSeriesCount() == 1)
                        axis.setLabel(collection.getSeries(0).getKey().toString());
                    plot.setRangeAxis((ValueAxis)axis);
                    plot.setDataset(collection);
                    plot.setRenderer(new XYLineAndShapeRenderer(showLines, true));
                    plot.getRenderer().setURLGenerator(new XYUrlGenerator(getRenderInfo()));
                    plot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());
                }

                plot.setDomainAxis((ValueAxis)createAxis(X_AXIS, descriptor, getLabel(labels, columnX)));
                return plot;
            }
            finally
            {
                rs.close();
            }
        }
        return null;
    }

    public Map<String, String> getDisplayColumns(QueryView view, boolean isXAxis)
    {
        return getDisplayColumns(view, true, false);
    }

    protected void addDataItem(XYSeries series, Map<String, Object> rowMap, String xcol, String ycol)
    {
        Object oX = rowMap.get(xcol);
        Object oY = rowMap.get(ycol);

        if (oX == null || oY == null) return;

        final Class cY = oY.getClass();
        final Class cX = oX.getClass();

        // right now we only deal with numeric types on the Y axis
        if (cY.isPrimitive() || Number.class.isAssignableFrom(cY))
        {
            if (cX.isPrimitive() || Number.class.isAssignableFrom(cX))
            {
                ChartRenderInfo info = getRenderInfo();
                Map<String, Object> colMap = null;

                if (info != null && info.getImageMapCallbackColumns().length != 0)
                {
                    colMap = new HashMap<>();
                    for (String colName : info.getImageMapCallbackColumns())
                    {
                        if (rowMap.containsKey(colName))
                            colMap.put(colName, rowMap.get(colName));
                    }
                }
                if (colMap != null)
                    series.add(new ExtraDataItem((Number)oX, (Number)oY, colMap));
                else
                    series.add(new XYDataItem((Number)oX, (Number)oY));
            }
        }
    }

    protected static class ExtraDataItem extends XYDataItem implements ImageMapDataItem
    {
        private Map<String, Object> _extraInfo;

        public ExtraDataItem(Number x, Number y, Map<String, Object> extraInfo)
        {
            super(x, y);
            _extraInfo = extraInfo;
        }

        public Map<String, Object> getExtraInfo()
        {
            return _extraInfo;
        }
    }

    protected static class XYUrlGenerator extends StandardUrlGenerator
    {
        public XYUrlGenerator(ChartRenderInfo info)
        {
            super(info);
        }

        protected void renderExtraColumns(XYDataset dataset, int series, int item, StringBuffer sb)
        {
            if (dataset instanceof XYSeriesCollection)
            {
                XYDataItem dataItem = ((XYSeriesCollection)dataset).getSeries(series).getDataItem(item);
                if (dataItem instanceof ImageMapDataItem)
                {
                    for (Map.Entry<String, Object> entry : ((ImageMapDataItem)dataItem).getExtraInfo().entrySet())
                    {
                        sb.append(" ,");
                        sb.append(entry.getKey());
                        sb.append(":");
                        sb.append(String.valueOf(entry.getValue()));
                    }
                }
            }
            else
                super.renderExtraColumns(dataset, series, item, sb);
        }
    }
}
