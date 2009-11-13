/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
package org.labkey.api.study.reports;

import jxl.format.Alignment;
import jxl.format.VerticalAlignment;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.arrays.DoubleArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.Report;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Stats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 3:35:36 PM
 */
public class Crosstab
{
    public static final String TOTAL_COLUMN = "CROSSTAB_TOTAL_COLUMN";
    public static final String TOTAL_ROW = "CROSSTAB_TOTAL_ROW";
    private Set<Stats.StatDefinition> statSet;
    private FieldKey _rowFieldKey;
    private FieldKey _colFieldKey;
    private FieldKey _statFieldKey;
    private Report.Results _results;

    List colHeaders = new  ArrayList<Object>();


    //TODO: This should be an arbitrary number of groupings in any combination!!
    //TODO: Improve memory usage
    Map<Object, Map<Object, DoubleArray>> crossTab = new LinkedHashMap<Object, Map<Object, DoubleArray>>();
    Map<Object, DoubleArray> rowDatasets = new HashMap<Object, DoubleArray>();
    Map<Object, DoubleArray> colDatasets = new HashMap<Object, DoubleArray>();
    DoubleArray grandTotalDataset = new DoubleArray();

    public Crosstab(Report.Results results, FieldKey rowFieldKey, FieldKey colFieldKey, FieldKey statFieldKey, Set<Stats.StatDefinition> statSet) throws SQLException
    {
        this.statSet = statSet;
        _results = results;
        _rowFieldKey = rowFieldKey;
        _colFieldKey = colFieldKey;
        _statFieldKey = statFieldKey;

        String rowFieldAlias = null;
        String colFieldAlias = null;
        String statFieldAlias = null;
        String statCol = null;

        // map the row, column and stat FieldKey names to result set aliases
        Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();

        if (fieldMap.containsKey(rowFieldKey))
            rowFieldAlias = fieldMap.get(rowFieldKey).getAlias();
        if (fieldMap.containsKey(colFieldKey))
            colFieldAlias = fieldMap.get(colFieldKey).getAlias();
        if (fieldMap.containsKey(statFieldKey))
            statFieldAlias = fieldMap.get(statFieldKey).getAlias();

        ResultSet rs = _results.getResultSet();

        try {
            //TODO: Use DisplayField for display & value extraction. Support Grouping fields with custom groupings
            while (rs.next())
            {
                Object rowVal = null;
                DoubleArray cellValues = null;
                DoubleArray colDataset = null;
                if (null != rowFieldAlias)
                {
                    rowVal = rs.getObject(rowFieldAlias);
                    Map<Object, DoubleArray> rowMap = crossTab.get(rowVal);
                    if (null == rowMap)
                    {
                        rowMap = new HashMap<Object, DoubleArray>();
                        crossTab.put(rowVal, rowMap);
                        if (null == colFieldAlias)
                            rowMap.put(statCol, new DoubleArray());

                        rowDatasets.put(rowVal, new DoubleArray());
                    }

                    cellValues = null;
                    colDataset = null;
                    if (null != colFieldAlias)
                    {
                        Object colVal = rs.getObject(colFieldAlias);
                        cellValues = rowMap.get(colVal);
                        if (null == cellValues)
                        {
                            cellValues = new DoubleArray();
                            rowMap.put(colVal, cellValues);
                            if (!colHeaders.contains(colVal))
                            {
                                colHeaders.add(colVal);
                                colDataset = new DoubleArray();
                                colDatasets.put(colVal, colDataset);
                            }
                        }

                        colDataset = colDatasets.get(colVal);
                    }
                }

                Object statFieldVal = rs.getObject(statFieldAlias);
                double d;
                if (statFieldVal instanceof Number)
                    d = ((Number) statFieldVal).doubleValue();
                else
                    d = null == statFieldVal ? Double.NaN : 1.0;

                if (null != cellValues)
                    cellValues.add(d);
                if (null != colDataset)
                    colDataset.add(d);

                Collections.sort(colHeaders, new GenericComparator());

                grandTotalDataset.add(d);
                if (null != rowFieldAlias)
                    rowDatasets.get(rowVal).add(d);
            }
        }
        finally
        {
            try { rs.close(); } catch (SQLException e) {}
        }
    }

    public String getDescription()
    {
        Stats.StatDefinition stat = null;
        if (getStatSet().size() == 1)
            stat = getStatSet().toArray(new Stats.StatDefinition[1])[0];

        String statFieldLabel = getFieldLabel(getStatField());
        String rowFieldLabel = getFieldLabel(getRowField());
        String colFieldLabel = getFieldLabel(getColField());

        String title = (null != stat ? stat.getName() + " " : "") + statFieldLabel + " by " + rowFieldLabel;
        if (null != getColField())
            title += ", " + colFieldLabel;

        return title;
    }

    public String getFieldLabel(FieldKey fieldKey)
    {
        if (null == fieldKey)
            return "";

        ColumnInfo col = _results.getFieldMap().get(fieldKey);
        if (null != col)
            return col.getLabel();

        return fieldKey.getName();
    }

    public List<Object> getRowHeaders()
    {
        List<Object> l = new ArrayList(crossTab.keySet());
        Collections.sort(l, new GenericComparator());
        return l;
    }

    public List<Object> getColHeaders()
    {
        return colHeaders;
    }

    private static class GenericComparator implements Comparator
    {
        public int compare(Object o1, Object o2)
        {
            if (null == o1)
            {
                if (null == o2)
                    return 0;
                else
                    return -1;
            }
            if (null == o2)
                return 1;

            if (o1 instanceof Comparable)
                return ((Comparable) o1).compareTo(o2);
            else
                return 0;
        }
    }

    public Stats getStats(Object rowHeader, Object colHeader)
    {
        if (TOTAL_COLUMN.equals(colHeader))
        {
            if (TOTAL_ROW.equals(rowHeader))
                return new Stats.DoubleStats(grandTotalDataset.toArray(null), statSet);
            else
                return new Stats.DoubleStats(rowDatasets.get(rowHeader).toArray(null), statSet);
        }
        else if (TOTAL_ROW.equals(rowHeader))
        {
            return new Stats.DoubleStats(colDatasets.get(colHeader).toArray(null), statSet);
        }
        else
        {
            Map<Object, DoubleArray> rowMap = crossTab.get(rowHeader);
            DoubleArray data = rowMap.get(colHeader);
            if (null != data)
                return new Stats.DoubleStats(data.toArray(null), statSet);
            else
                return new Stats.DoubleStats(new double[0], statSet);
        }
    }

    public Set<Stats.StatDefinition> getStatSet()
    {
        return statSet;
    }

    public FieldKey getRowField()
    {
        return _rowFieldKey;
    }

    public FieldKey getColField()
    {
        return _colFieldKey;
    }

    public FieldKey getStatField()
    {
        return _statFieldKey;
    }

    public Report.Results getResults()
    {
        return _results;
    }

    public ExcelWriter getExcelWriter()
    {
        return new CrosstabExcelWriter(this);
    }

    public static class CrosstabExcelWriter extends ExcelWriter
    {
        private Crosstab _crosstab;
        private static final String STAT_COLUMN = "Stat";

        public CrosstabExcelWriter(Crosstab crosstab)
        {
            _crosstab = crosstab;

            List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

            columns.add(new CrosstabDisplayColumn(_crosstab.getFieldLabel(crosstab.getRowField())));

            if (_crosstab.getStatSet().size() > 1)
                columns.add(new CrosstabDisplayColumn(STAT_COLUMN, String.class));

            for (Object col : _crosstab.getColHeaders())
                columns.add(new CrosstabDisplayColumn(StringUtils.trimToEmpty(ConvertUtils.convert(col)), Double.class));

            columns.add(new CrosstabDisplayColumn("Total", Double.class));

            setDisplayColumns(columns);

            List<String> headers = new ArrayList<String>();

            headers.add(_crosstab.getDescription());
            headers.add(_crosstab.getFieldLabel(_crosstab.getColField()));

            setHeaders(headers);
        }

        protected WritableCellFormat getWrappingTextFormat() throws WriteException
        {
            WritableCellFormat format = new WritableCellFormat();
            format.setWrap(true);
            format.setVerticalAlignment(VerticalAlignment.TOP);
            format.setAlignment(Alignment.CENTRE);

            return format;
        }

        @Override
        public void renderGrid(WritableSheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, WriteException, MaxRowsExceededException
        {
            Map<String, Object> rowMap = new CaseInsensitiveHashMap<Object>();
            RenderContext ctx = new RenderContext(HttpView.currentContext());

            for (Object rowValue : _crosstab.getRowHeaders())
            {
                for (Stats.StatDefinition rowStat : _crosstab.getStatSet())
                {
                    if (_crosstab.getStatSet().size() > 1)
                    {
                        rowMap.put(_crosstab.getFieldLabel(_crosstab.getRowField()), rowValue);
                        rowMap.put(STAT_COLUMN, rowStat.getName());
                    }
                    else
                        rowMap.put(_crosstab.getFieldLabel(_crosstab.getRowField()), rowValue);

                    ctx.setRow(mapRow(rowValue, rowStat, rowMap));
                    renderGridRow(sheet, ctx, visibleColumns);
                }
            }

            // stat totals
            for (Stats.StatDefinition rowStat : _crosstab.getStatSet())
            {
                if (_crosstab.getStatSet().size() > 1)
                {
                    rowMap.put(_crosstab.getFieldLabel(_crosstab.getRowField()), "Total");
                    rowMap.put(STAT_COLUMN, rowStat.getName());
                }
                else
                    rowMap.put(_crosstab.getFieldLabel(_crosstab.getRowField()), "Total");

                ctx.setRow(mapRow(Crosstab.TOTAL_ROW, rowStat, rowMap));
                renderGridRow(sheet, ctx, visibleColumns);
            }
        }

        private Map<String, Object> mapRow(Object rowValue, Stats.StatDefinition rowStat, Map<String, Object> rowMap)
        {
            for (Object colVal : _crosstab.getColHeaders())
            {
                rowMap.put(StringUtils.trimToEmpty(ConvertUtils.convert(colVal)), _crosstab.getStats(rowValue, colVal).getStat(rowStat));
            }
            rowMap.put("total", _crosstab.getStats(rowValue, Crosstab.TOTAL_COLUMN).getStat(rowStat));
            return rowMap;
        }
    }

    public static class CrosstabDisplayColumn extends SimpleDisplayColumn
    {
        Class _valueClass = String.class;

        public CrosstabDisplayColumn(String caption, Class valueClass)
        {
            setCaption(caption);
            if (valueClass != null)
                _valueClass = valueClass;
        }
        public CrosstabDisplayColumn(Object info)
        {
            if (info != null)
            {
                _valueClass = info.getClass();
            }
            setCaption(StringUtils.trimToEmpty(ConvertUtils.convert(info)));
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            return ctx.get(getCaption());
        }

        @Override
        public Class getDisplayValueClass()
        {
            return _valueClass;
        }
    }
}
