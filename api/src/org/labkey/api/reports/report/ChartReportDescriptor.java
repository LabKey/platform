/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.math.NumberUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * User: Karl Lum
 * Date: Oct 5, 2006
 */

@Deprecated // can delete in release 21.2
public class ChartReportDescriptor extends ReportDescriptor
{
    public static final String CHART_XY = "1";
    public static final String CHART_TIME = "2";

    public static final String TYPE = "chartDescriptor";

    public enum Prop implements ReportProperty
    {
        columnXName,
        columnYName,
        isLogX,
        isLogY,
        chartType,
        height,
        width,
        showMultipleYAxis,
        showLines,
        showMultipleCharts,
        isVerticalOrientation,
    }

    public ChartReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public void setColumnYName(String[] columnYName)
    {
        _props.put(Prop.columnYName.toString(), Arrays.asList(columnYName));
    }

    public String[] getColumnYName()
    {
        final Object colY = _props.get(Prop.columnYName.toString());
        if (colY instanceof List)
            return ((List<String>)colY).toArray(new String[0]);
        else if (colY instanceof String)
            return new String[]{(String)colY};

        return new String[0];
    }

    public void setChartType(String chartType)
    {
        setProperty(Prop.chartType, chartType);
    }

    public String getChartType()
    {
        return Objects.toString(getProperty(Prop.chartType), CHART_XY);
    }

    public void setWidth(int width)
    {
        setProperty(Prop.width, width);
    }

    public int getWidth()
    {
        return NumberUtils.toInt(getProperty(Prop.width), 640);
    }

    public void setHeight(int height)
    {
        setProperty(Prop.height, height);
    }

    public int getHeight()
    {
        return NumberUtils.toInt(getProperty(Prop.height), 200);
    }

    @Override
    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {                 
            return Prop.columnYName.toString().equals(prop);
        }
        return true;
    }
}
