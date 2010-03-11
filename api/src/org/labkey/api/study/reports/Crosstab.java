/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
    private StatType _statType = StatType.unknown;

    List colHeaders = new  ArrayList<Object>();


    //TODO: This should be an arbitrary number of groupings in any combination!!
    //TODO: Improve memory usage
    Map<Object, Map<Object, List<Object>>> crossTab = new LinkedHashMap<Object, Map<Object, List<Object>>>();
    Map<Object, List<Object>> rowDatasets = new HashMap<Object, List<Object>>();
    Map<Object, List<Object>> colDatasets = new HashMap<Object, List<Object>>();
    List<Object> grandTotalDataset = new ArrayList<Object>();

    public enum StatType {
        numeric,
        string,
        unknown,
    }

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
        {
            statFieldAlias = fieldMap.get(statFieldKey).getAlias();
            ColumnInfo col = fieldMap.get(statFieldKey);
            if (Number.class.isAssignableFrom(col.getJavaClass()) || col.getJavaClass().isPrimitive())
                _statType = StatType.numeric;
            else if (String.class.isAssignableFrom(col.getJavaClass()))
                _statType = StatType.string;
        }

        ResultSet rs = _results.getResultSet();

        try {
            //TODO: Use DisplayField for display & value extraction. Support Grouping fields with custom groupings
            while (rs.next())
            {
                Object rowVal = null;
                List<Object> cellValues = null;
                List<Object> colDataset = null;
                if (null != rowFieldAlias)
                {
                    rowVal = rs.getObject(rowFieldAlias);
                    Map<Object, List<Object>> rowMap = crossTab.get(rowVal);
                    if (null == rowMap)
                    {
                        rowMap = new HashMap<Object, List<Object>>();
                        crossTab.put(rowVal, rowMap);
                        if (null == colFieldAlias)
                            rowMap.put(statCol, new ArrayList<Object>());

                        rowDatasets.put(rowVal, new ArrayList<Object>());
                    }

                    cellValues = null;
                    colDataset = null;
                    if (null != colFieldAlias)
                    {
                        Object colVal = rs.getObject(colFieldAlias);
                        cellValues = rowMap.get(colVal);
                        if (null == cellValues)
                        {
                            cellValues = new ArrayList<Object>();
                            rowMap.put(colVal, cellValues);
                            if (!colHeaders.contains(colVal))
                            {
                                colHeaders.add(colVal);
                                colDataset = new ArrayList<Object>();
                                colDatasets.put(colVal, colDataset);
                            }
                        }

                        colDataset = colDatasets.get(colVal);
                    }
                }

                Object statFieldVal = rs.getObject(statFieldAlias);
                if (statFieldVal == null)
                {
                    if (getStatType() == StatType.string)
                        statFieldVal = "";
                    else if (getStatType() == StatType.numeric)
                        statFieldVal = Double.NaN;
                }
                if (null != cellValues)
                    cellValues.add(statFieldVal);
                if (null != colDataset)
                    colDataset.add(statFieldVal);

                Collections.sort(colHeaders, new GenericComparator());

                grandTotalDataset.add(statFieldVal);
                if (null != rowFieldAlias)
                    rowDatasets.get(rowVal).add(statFieldVal);
            }
        }
        finally
        {
            try { rs.close(); } catch (SQLException e) {}
        }
    }

    public StatType getStatType()
    {
        return _statType;
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
                return createStat(grandTotalDataset, statSet);
            else
                return createStat(rowDatasets.get(rowHeader), statSet);
        }
        else if (TOTAL_ROW.equals(rowHeader))
        {
            return createStat(colDatasets.get(colHeader), statSet);
        }
        else
        {
            Map<Object, List<Object>> rowMap = crossTab.get(rowHeader);
            List<Object> data = rowMap.get(colHeader);
            return createStat(data, statSet);
        }
    }

    private Stats createStat(List<Object> data, Set<Stats.StatDefinition> statSet)
    {
        Object[] statData;
        StatType type = getStatType();

        if (type == StatType.numeric)
        {
            if (data != null)
                statData = data.toArray(new Number[data.size()]);
            else
                statData = new Number[0];
        }
        else if (type == StatType.string)
        {
            if (data != null)
                statData = data.toArray(new String[data.size()]);
            else
                statData = new String[0];
        }
        else
            throw new IllegalArgumentException("Non number/string data handed to stats");

        return Stats.getStats(statData, statSet);
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
            Class statColumnCls;

            if (_crosstab.getStatType() == StatType.numeric)
                statColumnCls = Double.class;
            else if (_crosstab.getStatType() == StatType.string)
                statColumnCls = String.class;
            else
                throw new IllegalArgumentException("Invalid Crosstab stat type");

            List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

            columns.add(new CrosstabDisplayColumn(_crosstab.getFieldLabel(crosstab.getRowField())));

            if (_crosstab.getStatSet().size() > 1)
                columns.add(new CrosstabDisplayColumn(STAT_COLUMN, String.class));

            for (Object col : _crosstab.getColHeaders())
                columns.add(new CrosstabDisplayColumn(StringUtils.trimToEmpty(ConvertUtils.convert(col)), statColumnCls));

            columns.add(new CrosstabDisplayColumn("Total", statColumnCls));

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
