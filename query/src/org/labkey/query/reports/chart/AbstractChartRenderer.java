/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.xy.XYDataset;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Table;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.chart.ChartRenderInfo;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.view.DataView;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Sep 28, 2006
 */
public abstract class AbstractChartRenderer implements ChartRenderer
{
    public static final int X_AXIS = 1;
    public static final int Y_AXIS = 2;

    protected ChartRenderInfo _renderInfo;

    protected ResultSet generateResultSet(ReportQueryView view) throws Exception
    {
        if (view != null)
        {
            view.getSettings().setMaxRows(Table.ALL_ROWS);
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            rgn.setAllowAsync(false);
            RenderContext ctx = dataView.getRenderContext();

            return rgn.getResultSet(ctx);
        }
        return null;
    }

    protected Axis createAxis(int type, ChartReportDescriptor descriptor, String label)
    {
        switch (type)
        {
            case X_AXIS:
                if (BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.isLogX)))
                {
                    LogarithmicAxis axis = new LogarithmicAxis(label);
                    axis.setStrictValuesFlag(false);
                    return axis;
                }
                else
                {
                    NumberAxis axis = new NumberAxis(label);
                    axis.setAutoRangeIncludesZero(false);
                    return axis;
                }
            case Y_AXIS:
                if (BooleanUtils.toBoolean(descriptor.getProperty(ChartReportDescriptor.Prop.isLogY)))
                {
                    LogarithmicAxis axis = new LogarithmicAxis(label);
                    axis.setStrictValuesFlag(false);
                    return axis;
                }
                else
                {
                    NumberAxis axis = new NumberAxis(label);
                    axis.setAutoRangeIncludesZero(false);
                    return axis;
                }
        }
        return null;
    }

    protected Map<String, String> getLabelMap(QueryView view)
    {
        Map<String, String> labelMap = new CaseInsensitiveHashMap<>();
        if (view != null)
        {
            for (DisplayColumn column : view.getDisplayColumns())
            {
                final ColumnInfo info = column.getColumnInfo();
                if (info != null)
                    labelMap.put(info.getAlias(), info.getLabel());
            }
        }
        return labelMap;
    }

    protected String getLabel(Map<String, String> labelMap, String columnName)
    {
        if (labelMap.containsKey(columnName))
            return labelMap.get(columnName);

        return columnName;
    }

    protected Map<String, String> getDisplayColumns(QueryView view, boolean isNumber, boolean isDate)
    {
        List<ColumnInfo> columns = new ArrayList<>();
        for (DisplayColumn col : view.getDisplayColumns())
        {
            ColumnInfo prop = col.getColumnInfo();
            if (prop == null)
                continue;
            
            if (isDate)
            {
                if (Date.class.isAssignableFrom(prop.getJavaClass()))
                {
                    columns.add(prop);
                    continue;
                }
            }

            if (isNumber && (prop.getJavaClass().isPrimitive() || Number.class.isAssignableFrom(prop.getJavaClass())))
            {
                columns.add(prop);
                continue;
            }
        }

        columns.sort(Comparator.comparing(ColumnInfo::getLabel));

        Map<String, String> displayColumns = new LinkedHashMap<>();
        for (ColumnInfo col : columns)
            displayColumns.put(col.getLabel(), col.getAlias());

        return displayColumns;
    }

    public void setRenderInfo(ChartRenderInfo info)
    {
        _renderInfo = info;
    }

    public ChartRenderInfo getRenderInfo()
    {
        return _renderInfo;
    }

    public interface ImageMapDataItem
    {
        public Map<String, Object> getExtraInfo();
    }

    public static class StandardUrlGenerator implements XYURLGenerator
    {
        private ChartRenderInfo _renderInfo;

        public StandardUrlGenerator(ChartRenderInfo renderInfo)
        {
            _renderInfo = renderInfo;
        }

        public String generateURL(XYDataset dataset, int series, int item)
        {
            if (_renderInfo != null && _renderInfo.getImageMapCallback() != null)
            {
                StringBuffer sb = new StringBuffer();

                Comparable key = dataset.getSeriesKey(series);
                Number x = dataset.getX(series, item);
                Number y = dataset.getY(series, item);

                sb.append("javascript:");
                sb.append(_renderInfo.getImageMapCallback());
                sb.append("({");
                sb.append("key: '");
                sb.append(key);
                sb.append("',x:");
                sb.append(x);
                sb.append(" ,y:");
                sb.append(y);

                // any additional column info
                renderExtraColumns(dataset, series, item, sb);
                sb.append("})");

                return sb.toString();
            }
            return null;
        }

        protected void renderExtraColumns(XYDataset dataset, int series, int item, StringBuffer sb)
        {
        }
    }

    public static class XYReportToolTipGenerator implements XYToolTipGenerator
    {
        public String generateToolTip(XYDataset dataset, int series, int item)
        {
            StringBuffer sb = new StringBuffer();

            Comparable key = dataset.getSeriesKey(series);
            Number x = dataset.getX(series, item);
            Number y = dataset.getY(series, item);

            sb.append(key);
            sb.append(" (");
            sb.append(x);
            sb.append(", ");
            sb.append(y);
            sb.append(")");

            return sb.toString();
        }
    }
}
