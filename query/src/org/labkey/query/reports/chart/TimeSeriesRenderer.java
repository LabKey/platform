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
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimePeriodValue;
import org.jfree.data.time.TimePeriodValues;
import org.jfree.data.time.TimePeriodValuesCollection;
import org.jfree.data.xy.XYDataset;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.chart.ChartRenderInfo;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;

import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Oct 2, 2006
 */
public class TimeSeriesRenderer extends AbstractChartRenderer implements ChartRenderer
{
    private static final TimeSeriesRenderer _instance = new TimeSeriesRenderer();

    public static TimeSeriesRenderer getInstance()
    {
        return _instance;
    }

    private TimeSeriesRenderer(){}

    public String getType()
    {
        return "2";
    }

    public String getName()
    {
        return "Time Series Plot";
    }

    public Plot createPlot(ChartReportDescriptor descriptor, ReportQueryView view) throws Exception
    {
        String columnX = descriptor.getProperty(ChartReportDescriptor.Prop.columnXName);

        // issue: 10818. Possibly a bug in jfree chart, but for time series plots, the collection must be ordered otherwise
        // the chart may not render properly, this is epecially evident when lines are shown between points, xy
        // charts do not seem to have the same problem.
        
        view.getSettings().getBaseSort().insertSortColumn(columnX);
        ResultSet rs = generateResultSet(view);
        if (rs != null)
        {
            try {
                Map<String, String> labels = getLabelMap(view);
                Map<String, TimePeriodValues> datasets = new HashMap<>();
                for (String columnName : descriptor.getColumnYName())
                {
                    if (!StringUtils.isEmpty(columnName))
                        datasets.put(columnName, new TimePeriodValues(getLabel(labels, columnName)));//, Day.class));
                }

                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                // create a jfreechart dataset
                while (rs.next())
                {
                    Map<String, Object> rowMap = factory.getRowMap(rs);

                    for (Map.Entry<String, TimePeriodValues> series : datasets.entrySet())
                    {
                        addDataItem(series.getValue(), rowMap, columnX, series.getKey());
                    }
                }

                boolean isMultiYAxis = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleYAxis));
                boolean showLines = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showLines));
                boolean showMultiPlot = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.showMultipleCharts));

                XYPlot plot = new XYPlot();
                TimePeriodValuesCollection collection = new TimePeriodValuesCollection();

                for (TimePeriodValues series : datasets.values())
                    collection.addSeries(series);

                if (showMultiPlot && datasets.size() > 1)
                {
                    boolean isVertical = BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.isVerticalOrientation));
                    plot = isVertical ?
                            new CombinedDomainXYPlot((ValueAxis)createAxis(X_AXIS, descriptor, null)) :
                            new CombinedRangeXYPlot((ValueAxis)createAxis(Y_AXIS, descriptor, null));

                    for (TimePeriodValues series : datasets.values())
                    {
                        XYPlot subPlot = new XYPlot();

                        subPlot.setDataset(new TimePeriodValuesCollection(series));
                        subPlot.setRangeAxis((ValueAxis)createAxis(Y_AXIS, descriptor, series.getKey().toString()));
                        subPlot.setDomainAxis((ValueAxis)createAxis(X_AXIS, descriptor, getLabel(labels, columnX)));
                        subPlot.setRenderer(new XYLineAndShapeRenderer(showLines, true));
                        subPlot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
                        subPlot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        if (isVertical)
                            ((CombinedDomainXYPlot)plot).add(subPlot);
                        else
                            ((CombinedRangeXYPlot)plot).add(subPlot);
                    }
                }
                else if (isMultiYAxis && collection.getSeriesCount() > 0)
                {
                    for (int idx=0; idx < collection.getSeriesCount(); idx++)
                    {
                        TimePeriodValues timeSeries = collection.getSeries(idx);
                        plot.setDataset(idx, new TimePeriodValuesCollection(timeSeries));

                        Axis axis = createAxis(Y_AXIS, descriptor, timeSeries.getKey().toString());
                        plot.setRangeAxis(idx, (ValueAxis)axis);
                        plot.setRenderer(idx, new XYLineAndShapeRenderer(showLines, true));
                        plot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
                        plot.getRenderer().setToolTipGenerator(new XYReportToolTipGenerator());

                        plot.mapDatasetToRangeAxis(idx, idx);
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
                    plot.getRenderer().setURLGenerator(new UrlGenerator(getRenderInfo()));
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
        if (isXAxis)
            return getDisplayColumns(view, false, true);
        else
            return getDisplayColumns(view, true, false);
    }

    protected void addDataItem(TimePeriodValues series, Map<String, Object> rowMap, String xcol, String ycol)
    {
        Object oX = rowMap.get(xcol);
        Object oY = rowMap.get(ycol);

        if (oX == null || oY == null) return;

        final Class cY = oY.getClass();
        final Class cX = oX.getClass();

        // right now we only deal with numeric types on the Y axis
        if (cY.isPrimitive() || Number.class.isAssignableFrom(cY))
        {
            if (Date.class.isAssignableFrom(cX))
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
                    series.add(new ExtraDataItem(new Day((Date)oX, RegularTimePeriod.DEFAULT_TIME_ZONE), (Number)oY, colMap));
                else
                    series.add(new Day((Date)oX, RegularTimePeriod.DEFAULT_TIME_ZONE), (Number)oY);
            }
        }
    }

    public Axis createAxis(int type, ChartReportDescriptor descriptor, String label)
    {
        if (type == X_AXIS)
            return new DateAxis(label);

        return super.createAxis(type, descriptor, label);
    }

    protected static class ExtraDataItem extends TimePeriodValue implements ImageMapDataItem
    {
        private Map<String, Object> _extraInfo;

        public ExtraDataItem(RegularTimePeriod period, Number y, Map<String, Object> extraInfo)
        {
            super(period, y);
            _extraInfo = extraInfo;
        }

        public Map<String, Object> getExtraInfo()
        {
            return _extraInfo;
        }
    }

    protected static class UrlGenerator extends StandardUrlGenerator
    {
        public UrlGenerator(ChartRenderInfo info)
        {
            super(info);
        }

        protected void renderExtraColumns(XYDataset dataset, int series, int item, StringBuffer sb)
        {
            if (dataset instanceof TimePeriodValuesCollection)
            {
                TimePeriodValue dataItem = ((TimePeriodValuesCollection)dataset).getSeries(series).getDataItem(item);
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
