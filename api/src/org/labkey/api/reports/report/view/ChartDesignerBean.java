/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.ContainerUser;

import java.util.List;

/**
 * User: Karl Lum
 * Date: Dec 4, 2007
 */
public class ChartDesignerBean extends ReportDesignBean
{
    protected String _columnXName;
    protected String[] _columnYName = new String[0];
    protected String _chartType = "1";
    protected boolean _isLogX;
    protected boolean _isLogY;
    protected int _height = 200;
    protected int _width = 640;
    protected boolean _showMultipleYAxis;
    private boolean _showLines;
    private boolean _showMultipleCharts;
    private boolean _isVerticalOrientation;
    protected String _imageMapName;
    protected String _imageMapCallback;
    protected String[] _imageMapCallbackColumns;

    public enum Prop
    {
        columnXName,
        columnYName,
        isLogX,
        isLogY,
        chartType,
        columnXLabel,
        columnYLabel,
        height,
        width,
        showMultipleYAxis,
        filterParam,
        showLines,
        showMultipleCharts,
        isVerticalOrientation,
    }

    public ChartDesignerBean(){}
    public ChartDesignerBean(QuerySettings settings)
    {
        super(settings);
    }
    
    public String getColumnXName()
    {
        return _columnXName;
    }

    public void setColumnXName(String columnXName)
    {
        _columnXName = columnXName;
    }

    public String[] getColumnYName()
    {
        return _columnYName;
    }

    public void setColumnYName(String[] columnYName)
    {
        _columnYName = columnYName;
    }

    public String getChartType()
    {
        return _chartType;
    }

    public void setChartType(String chartType)
    {
        _chartType = chartType;
    }

    public boolean isLogX()
    {
        return _isLogX;
    }

    public void setLogX(boolean logX)
    {
        _isLogX = logX;
    }

    public boolean isLogY()
    {
        return _isLogY;
    }

    public void setLogY(boolean logY)
    {
        _isLogY = logY;
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

    public boolean isShowMultipleYAxis()
    {
        return _showMultipleYAxis;
    }

    public void setShowMultipleYAxis(boolean showMultipleYAxis)
    {
        _showMultipleYAxis = showMultipleYAxis;
    }

    public boolean isShowLines()
    {
        return _showLines;
    }

    public void setShowLines(boolean showLines)
    {
        _showLines = showLines;
    }

    public boolean isShowMultipleCharts()
    {
        return _showMultipleCharts;
    }

    public void setShowMultipleCharts(boolean showMultipleCharts)
    {
        _showMultipleCharts = showMultipleCharts;
    }

    public boolean isVerticalOrientation()
    {
        return _isVerticalOrientation;
    }

    public void setVerticalOrientation(boolean verticalOrientation)
    {
        _isVerticalOrientation = verticalOrientation;
    }

    public String getImageMapName()
    {
        return _imageMapName;
    }

    public void setImageMapName(String imageMapName)
    {
        _imageMapName = imageMapName;
    }

    public String getImageMapCallback()
    {
        return _imageMapCallback;
    }

    public void setImageMapCallback(String imageMapCallback)
    {
        _imageMapCallback = imageMapCallback;
    }

    public String[] getImageMapCallbackColumns()
    {
        return _imageMapCallbackColumns;
    }

    public void setImageMapCallbackColumns(String[] imageMapCallbackColumns)
    {
        _imageMapCallbackColumns = imageMapCallbackColumns;
    }

    public Report getReport(ContainerUser cu) throws Exception
    {
        Report report = super.getReport(cu);
        if (report != null)
        {
            ChartReportDescriptor descriptor = (ChartReportDescriptor)report.getDescriptor();

            if (!StringUtils.isEmpty(_columnXName))
                descriptor.setProperty(ChartReportDescriptor.Prop.columnXName, _columnXName);
            if (_columnYName.length > 0)
                descriptor.setColumnYName(_columnYName);

            descriptor.setChartType(_chartType);
            descriptor.setProperty(ChartReportDescriptor.Prop.isLogX, _isLogX);
            descriptor.setProperty(ChartReportDescriptor.Prop.isLogY, _isLogY);
            descriptor.setHeight(_height);
            descriptor.setWidth(_width);
            descriptor.setProperty(ChartReportDescriptor.Prop.showMultipleYAxis, _showMultipleYAxis);
            descriptor.setProperty(ChartReportDescriptor.Prop.showLines, _showLines);
            descriptor.setProperty(ChartReportDescriptor.Prop.showMultipleCharts, _showMultipleCharts);
            descriptor.setProperty(ChartReportDescriptor.Prop.isVerticalOrientation, _isVerticalOrientation);
        }
        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        if (!StringUtils.isEmpty(_columnXName))
            list.add(new Pair<>(Prop.columnXName.toString(), _columnXName));

        for (String name : _columnYName)
        {
            if (!StringUtils.isEmpty(name))
                list.add(new Pair<>(Prop.columnYName.toString(), name));
        }
        list.add(new Pair<>(Prop.chartType.toString(), _chartType));
        list.add(new Pair<>(Prop.height.toString(), String.valueOf(_height)));
        list.add(new Pair<>(Prop.width.toString(), String.valueOf(_width)));

        if (_isLogX)
            list.add(new Pair<>(Prop.isLogX.toString(), "true"));
        if (_isLogY)
            list.add(new Pair<>(Prop.isLogY.toString(), "true"));
        if (_showMultipleYAxis)
            list.add(new Pair<>(Prop.showMultipleYAxis.toString(), "true"));
        if (_showLines)
            list.add(new Pair<>(Prop.showLines.toString(), "true"));
        if (_showMultipleCharts)
            list.add(new Pair<>(Prop.showMultipleCharts.toString(), "true"));
        if (_isVerticalOrientation)
            list.add(new Pair<>(Prop.isVerticalOrientation.toString(), "true"));

        return list;
    }
}
