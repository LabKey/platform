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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Dec 3, 2007
 */
public class GWTChart implements IsSerializable
{
    private String _reportType;
    private String _reportName;
    private String _reportDescription;
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _columnXName;
    private String[] _columnYName = new String[0];
    private String _chartType;
    private boolean _isLogX;
    private boolean _isLogY;
    private int _height;
    private int _width;
    private boolean _showMultipleYAxis;
    private boolean _shared;
    private boolean _showLines;
    private boolean _showMultipleCharts;
    private boolean _isVerticalOrientation;
    /**
     * Map of column captions to aliases
     */
    private Map<String, String> _properties = new HashMap<String, String>();

    public GWTChart()
    {
    }

    public String getReportType()
    {
        return _reportType;
    }

    public void setReportType(String reportType)
    {
        _reportType = reportType;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
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

    public String getReportName()
    {
        return _reportName;
    }

    public void setReportName(String reportName)
    {
        if (reportName != null)
            _reportName = reportName.trim();
    }

    public boolean isShowMultipleYAxis()
    {
        return _showMultipleYAxis;
    }

    public void setShowMultipleYAxis(boolean showMultipleYAxis)
    {
        _showMultipleYAxis = showMultipleYAxis;
    }

    public String getReportDescription()
    {
        return _reportDescription;
    }

    public void setReportDescription(String reportDescription)
    {
        _reportDescription = reportDescription;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public void setShared(boolean shared)
    {
        _shared = shared;
    }

    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperty(String key, String value)
    {
        _properties.put(key, value);
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

    public void validate(List<String> errors)
    {
        if (_columnXName == null || _columnXName.length() == 0)
            errors.add("A measurement for the horizontal axis must be selected");

        if (_columnYName.length == 0)
            errors.add("At least one vertical axis measurement must be selected");
    }
}
