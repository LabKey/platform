/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.assay.nab;

import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.labels.StandardXYSeriesLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.YIntervalRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.dilution.DilutionMaterialKey;
import org.labkey.api.assay.dilution.DilutionSummary;
import org.labkey.api.assay.nab.view.RunDetailOptions;
import org.labkey.api.data.Container;
import org.labkey.api.data.statistics.DoublePoint;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.study.WellData;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;

import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Apr 21, 2010 12:50:55 PM
 */
public class NabGraph
{
    public static final int DEFAULT_WIDTH = 425;
    public static final int DEFAULT_HEIGHT = 300;
    public static final int DEFAULT_GRAPHS_PER_ROW = 2;
    public static final int DEFAULT_MAX_SAMPLES_PER_GRAPH = 5;

    private static final Color[] GRAPH_COLORS = {
            ChartColor.DARK_BLUE,
            ChartColor.DARK_RED,
            ChartColor.DARK_GREEN,
            ChartColor.DARK_YELLOW,
            ChartColor.DARK_MAGENTA,
            ChartColor.DARK_CYAN,

            ChartColor.VERY_LIGHT_BLUE,
            ChartColor.VERY_LIGHT_RED,
            ChartColor.VERY_LIGHT_GREEN,
            ChartColor.VERY_LIGHT_YELLOW,
            ChartColor.VERY_LIGHT_MAGENTA,
            ChartColor.VERY_LIGHT_CYAN,

            ChartColor.VERY_DARK_BLUE,
            ChartColor.VERY_DARK_RED,
            ChartColor.VERY_DARK_GREEN,
            ChartColor.VERY_DARK_YELLOW,
            ChartColor.VERY_DARK_MAGENTA,
            ChartColor.VERY_DARK_CYAN,

            ChartColor.LIGHT_BLUE,
            ChartColor.LIGHT_RED,
            ChartColor.LIGHT_GREEN,
            ChartColor.LIGHT_YELLOW,
            ChartColor.LIGHT_MAGENTA,
            ChartColor.LIGHT_CYAN
    };

    public static class Config
    {
        private int[] _cutoffs;
        private boolean _lockAxes = false;
        private String _captionColumn;
        private String _chartTitle;
        private int _height = DEFAULT_HEIGHT;
        private int _width = DEFAULT_WIDTH;
        private int _firstSample = 0;
        private int _maxSamples = -1;
        private String _yAxisLabel = "Percent Neutralization";
        private String _xAxisLabel = "Dilution/Concentration";
        private RunDetailOptions.DataIdentifier _dataIdentifier = RunDetailOptions.DataIdentifier.DefaultFormat;

        public int[] getCutoffs()
        {
            return _cutoffs;
        }

        public void setCutoffs(int[] cutoffs)
        {
            _cutoffs = cutoffs;
        }

        public boolean isLockAxes()
        {
            return _lockAxes;
        }

        public void setLockAxes(boolean lockAxes)
        {
            _lockAxes = lockAxes;
        }

        public String getCaptionColumn()
        {
            return _captionColumn;
        }

        public void setCaptionColumn(String captionColumn)
        {
            _captionColumn = captionColumn;
        }

        public String getChartTitle()
        {
            return _chartTitle;
        }

        public void setChartTitle(String chartTitle)
        {
            _chartTitle = chartTitle;
        }

        public int getHeight()
        {
            return _height;
        }

        public void setHeight(int height)
        {
            _height = height;
        }

        public int getWidth()
        {
            return _width;
        }

        public void setWidth(int width)
        {
            _width = width;
        }

        public int getFirstSample()
        {
            return _firstSample;
        }

        public void setFirstSample(int firstSample)
        {
            _firstSample = firstSample;
        }

        public int getMaxSamples()
        {
            return _maxSamples;
        }

        public void setMaxSamples(int maxSamples)
        {
            _maxSamples = maxSamples;
        }

        public String getyAxisLabel()
        {
            return _yAxisLabel;
        }

        public void setyAxisLabel(String yAxisLabel)
        {
            _yAxisLabel = yAxisLabel;
        }

        public String getxAxisLabel()
        {
            return _xAxisLabel;
        }

        public void setxAxisLabel(String xAxisLabel)
        {
            _xAxisLabel = xAxisLabel;
        }

        public RunDetailOptions.DataIdentifier getDataIdentifier()
        {
            return _dataIdentifier;
        }

        public void setDataIdentifier(RunDetailOptions.DataIdentifier dataIdentifier)
        {
            _dataIdentifier = dataIdentifier;
        }
    }

    private static String getCaption(DilutionSummary summary, RunDetailOptions.DataIdentifier dataIdentifier)
    {
        DilutionMaterialKey materialKey = summary.getMaterialKey();
        return materialKey.getDisplayString(dataIdentifier);
    }

    private static String formatCaption(Container c, Object captionValue)
    {
        if (captionValue instanceof Date)
        {
            Date date = (Date) captionValue;
            if (date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0)
                return DateUtil.formatDate(c, date);
            else
                return DateUtil.formatDateTime(c, date);
        }
        else
            return captionValue.toString();
    }

    public static void renderChartPNG(Container c, HttpServletResponse response, Map<DilutionSummary, DilutionAssayRun> summaries, Config config) throws IOException, FitFailedException
    {
        RunDetailOptions.DataIdentifier identifier = config.getDataIdentifier();
        if (identifier == RunDetailOptions.DataIdentifier.DefaultFormat)
        {
            // ensure captions are unique
            Set<String> shortCaptions = new HashSet<>();
            for (DilutionSummary summary : summaries.keySet())
            {
                String shortCaption = getCaption(summary, identifier);
                if (shortCaptions.contains(shortCaption))
                {
                    identifier = RunDetailOptions.DataIdentifier.LongFormat;
                    break;
                }
                shortCaptions.add(shortCaption);
            }
        }
        List<Pair<String, DilutionSummary>> summaryMap = new ArrayList<>();
        for (Map.Entry<DilutionSummary, DilutionAssayRun> sampleEntry : summaries.entrySet())
        {
            String caption = null;
            DilutionSummary summary = sampleEntry.getKey();
            if (config.getCaptionColumn() != null)
            {
                Object value = summary.getFirstWellGroup().getProperty(config.getCaptionColumn());
                if (value != null)
                    caption = formatCaption(c, value);
                else
                {
                    Map<PropertyDescriptor, Object> runProperties = sampleEntry.getValue().getRunProperties();
                    for (Map.Entry<PropertyDescriptor, Object> runProperty : runProperties.entrySet())
                    {
                        if (config.getCaptionColumn().equals(runProperty.getKey().getName()) && runProperty.getValue() != null)
                            caption = formatCaption(c, runProperty.getValue());
                    }
                }
            }
            if (caption == null || caption.length() == 0)
                caption = getCaption(summary, identifier);
            summaryMap.add(new Pair<>(caption, summary));
        }
        renderChartPNG(response, summaryMap, config);
    }

    public static void renderChartPNG(Container c, HttpServletResponse response, DilutionAssayRun assay, Config config) throws IOException, FitFailedException
    {
        Map<DilutionSummary, DilutionAssayRun> samples = new LinkedHashMap<>();
        for (DilutionSummary summary : assay.getSummaries())
        {
            if (!summary.isBlank())
                samples.put(summary, assay);
        }
        if (config.getCutoffs() == null)
            config.setCutoffs(assay.getCutoffs());
        renderChartPNG(c, response, samples, config);
    }

    public static void renderChartPNG(HttpServletResponse response, List<Pair<String, DilutionSummary>> dilutionSummaries, Config config) throws IOException, FitFailedException
    {
        XYSeriesCollection curvesDataset = new XYSeriesCollection();
        XYSeriesCollection pointDataset = new XYSeriesCollection();
        YIntervalSeriesCollection cvDataset = new YIntervalSeriesCollection();          // render coefficient of variation
        JFreeChart chart = ChartFactory.createXYLineChart(config.getChartTitle(), null, config.getyAxisLabel(), curvesDataset, PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = chart.getXYPlot();

        plot.setDataset(1, pointDataset);
        plot.setDataset(2, cvDataset);

        // configure renderers
        plot.getRenderer(0).setStroke(new BasicStroke(1.5f));
        if (config.isLockAxes())
            plot.getRangeAxis().setRange(-20, 120);
        XYLineAndShapeRenderer pointRenderer = new XYLineAndShapeRenderer(true, true);
        plot.setRenderer(1, pointRenderer);
        pointRenderer.setStroke(new BasicStroke(
                0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[]{4.0f, 4.0f}, 0.0f));
        plot.getRenderer(0).setSeriesVisibleInLegend(false);
        pointRenderer.setShapesFilled(true);

        YIntervalRenderer cvRenderer = new YIntervalRenderer(){
            @Override
            public Shape getItemShape(int row, int column)
            {
                // override with an empty shape since we don't want to render points
                // in the CV lines
                return new Rectangle();
            }
        };
        cvRenderer.setBaseSeriesVisible(false);
        plot.setRenderer(2, cvRenderer);

        // Issue 14405: NAb graphs cannot display if same ptid/visit used for more than one sample
        // XYSeriesCollection doesn't allow multiple series to have the same key
        // so we give them unique IDs then use the series description as the legend label.
        plot.getRenderer(1).setLegendItemLabelGenerator(new StandardXYSeriesLabelGenerator() {
            @Override
            public String generateLabel(XYDataset dataset, int series)
            {
                XYSeriesCollection collection = (XYSeriesCollection)dataset;
                XYSeries xySeries = collection.getSeries(series);
                return xySeries.getDescription();
            }
        });

        int count = 0;
        for (Pair<String, DilutionSummary> summaryEntry : dilutionSummaries)
        {
            count++;
            if ((config.getFirstSample() > 0 && count <= config.getFirstSample()) || // before the first sample we want to show
                (config.getMaxSamples() >= 0 && count > config.getMaxSamples() + config.getFirstSample()))// ) // after the last sample we want to show
            {
                continue;
            }

            String sampleId = summaryEntry.getKey();
            DilutionSummary summary = summaryEntry.getValue();
            XYSeries pointSeries = new XYSeries(sampleId + ", point" + count);
            YIntervalSeries cvSeries = new YIntervalSeries(sampleId);
            pointSeries.setDescription(sampleId);
            for (WellData well : summary.getWellData())
            {
                double percentage = 100 * summary.getPercent(well);
                double dilution = summary.getDilution(well);
                pointSeries.add(dilution, percentage);

                double plusMinus = Math.abs(summary.getPlusMinus(well) * 100);
                if (plusMinus > 0)
                    cvSeries.add(dilution, percentage, percentage - plusMinus, percentage + plusMinus);
            }
            if (cvSeries.getItemCount() > 0)
                cvDataset.addSeries(cvSeries);

            // issue 15448: since x-axis will be plotted on log scale, skip any series with dilution/concentration of <= zero (doesn't make sense for this assay)
            if (pointSeries.getMinX() <= 0)
                continue;

            pointDataset.addSeries(pointSeries);
            int pointDatasetCount = pointDataset.getSeriesCount();
            Color currentColor = GRAPH_COLORS[(pointDatasetCount - 1) % GRAPH_COLORS.length];
            plot.getRenderer(0).setSeriesPaint(pointDatasetCount - 1, currentColor);

            try
            {
                DoublePoint[] curve = summary.getCurve();
                XYSeries curvedSeries = new XYSeries(sampleId + ", curved" + count);
                for (DoublePoint point : curve)
                    curvedSeries.add(point.getX(), point.getY());
                curvesDataset.addSeries(curvedSeries);
                plot.getRenderer(1).setSeriesPaint(curvesDataset.getSeriesCount() - 1, currentColor);
                if (cvSeries.getItemCount() > 0)
                    plot.getRenderer(2).setSeriesPaint(cvDataset.getSeriesCount() - 1, currentColor);
            }
            catch (FitFailedException e)
            {
                // fall through; we'll just graph those that can be graphed.
            }
        }

        chart.getXYPlot().setBackgroundPaint(Color.WHITE);
        chart.getXYPlot().setDomainGridlinePaint(Color.LIGHT_GRAY);
        chart.getXYPlot().setRangeGridlinePaint(Color.LIGHT_GRAY);
        chart.getXYPlot().setDomainAxis(new LogarithmicAxis(config.getxAxisLabel()));
        chart.getXYPlot().addRangeMarker(new ValueMarker(0f, Color.DARK_GRAY, new BasicStroke()));
        for (int cutoff : config.getCutoffs())
            chart.getXYPlot().addRangeMarker(new ValueMarker(cutoff));
        response.setContentType("image/png");
        ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, config.getWidth(), config.getHeight());
    }
}
