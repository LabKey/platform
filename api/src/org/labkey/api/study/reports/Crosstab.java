/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Stats;

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
    private Results _results;
    private StatType _statType = StatType.unknown;

    List colHeaders = new  ArrayList<Object>();


    //TODO: This should be an arbitrary number of groupings in any combination!!
    //TODO: Improve memory usage
    Map<Object, Map<Object, List<Object>>> crossTab = new LinkedHashMap<>();
    Map<Object, List<Object>> rowDatasets = new HashMap<>();
    Map<Object, List<Object>> colDatasets = new HashMap<>();
    List<Object> grandTotalDataset = new ArrayList<>();

    public enum StatType {
        numeric,
        string,
        unknown,
    }

    public Crosstab(Results results, FieldKey rowFieldKey, FieldKey colFieldKey, FieldKey statFieldKey, Set<Stats.StatDefinition> statSet) throws SQLException
    {
        this.statSet = statSet;
        _results = results;
        _rowFieldKey = rowFieldKey;
        _colFieldKey = colFieldKey;
        _statFieldKey = statFieldKey;

        int rowFieldIndex = 0;
        int colFieldIndex = 0;
        int statFieldIndex = 0;
        String statCol = null;

        try {
            // map the row, column and stat FieldKey names to result set aliases
            Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();

            if (fieldMap.containsKey(rowFieldKey))
                rowFieldIndex = _results.findColumn(_rowFieldKey);
            if (fieldMap.containsKey(colFieldKey))
                colFieldIndex = _results.findColumn(_colFieldKey);
            if (fieldMap.containsKey(statFieldKey))
            {
                statFieldIndex = _results.findColumn(_statFieldKey);
                ColumnInfo col = fieldMap.get(statFieldKey);
                if (Number.class.isAssignableFrom(col.getJavaClass()) || col.getJavaClass().isPrimitive())
                    _statType = StatType.numeric;
                else if (String.class.isAssignableFrom(col.getJavaClass()))
                    _statType = StatType.string;
            }
            else
                throw new RuntimeException("The specified statistics column: " + _statFieldKey + " is not available in this view");

//        ResultSet rs = _results.getResultSet();

            //TODO: Use DisplayField for display & value extraction. Support Grouping fields with custom groupings
            while (_results.next())
            {
                Object rowVal = null;
                List<Object> cellValues = null;
                List<Object> colDataset = null;
                if (0 != rowFieldIndex)
                {
                    rowVal = _results.getObject(rowFieldIndex);
                    Map<Object, List<Object>> rowMap = crossTab.get(rowVal);
                    if (null == rowMap)
                    {
                        rowMap = new HashMap<>();
                        crossTab.put(rowVal, rowMap);
                        if (0 == colFieldIndex)
                            rowMap.put(statCol, new ArrayList<>());

                        rowDatasets.put(rowVal, new ArrayList<>());
                    }

                    cellValues = null;
                    colDataset = null;
                    if (0 != colFieldIndex)
                    {
                        Object colVal = _results.getObject(colFieldIndex);
                        cellValues = rowMap.get(colVal);
                        if (null == cellValues)
                        {
                            cellValues = new ArrayList<>();
                            rowMap.put(colVal, cellValues);
                            if (!colHeaders.contains(colVal))
                            {
                                colHeaders.add(colVal);
                                colDataset = new ArrayList<>();
                                colDatasets.put(colVal, colDataset);
                            }
                        }

                        colDataset = colDatasets.get(colVal);
                    }
                }

                Object statFieldVal = _results.getObject(statFieldIndex);
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
                if (0 != rowFieldIndex)
                    rowDatasets.get(rowVal).add(statFieldVal);
            }
        }
        finally
        {
            ResultSetUtil.close(_results);
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

    public Results getResults()
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
            super(ExcelDocumentType.xls);
            _crosstab = crosstab;
            Class statColumnCls;

            if (_crosstab.getStatType() == StatType.numeric)
                statColumnCls = Double.class;
            else if (_crosstab.getStatType() == StatType.string)
                statColumnCls = String.class;
            else
                throw new IllegalArgumentException("Invalid Crosstab stat type");

            List<DisplayColumn> columns = new ArrayList<>();

            columns.add(new CrosstabDisplayColumn(_crosstab.getFieldLabel(crosstab.getRowField())));

            if (_crosstab.getStatSet().size() > 1)
                columns.add(new CrosstabDisplayColumn(STAT_COLUMN, String.class));

            for (Object col : _crosstab.getColHeaders())
                columns.add(new CrosstabDisplayColumn(StringUtils.trimToEmpty(ConvertUtils.convert(col)), statColumnCls));

            columns.add(new CrosstabDisplayColumn("Total", statColumnCls));

            setDisplayColumns(columns);

            List<String> headers = new ArrayList<>();

            headers.add(_crosstab.getDescription());
            headers.add(_crosstab.getFieldLabel(_crosstab.getColField()));

            setHeaders(headers);
        }

        protected CellStyle getWrappingTextFormat()
        {
            CellStyle format = _workbook.createCellStyle();
            format.setWrapText(true);
            format.setVerticalAlignment(CellStyle.VERTICAL_TOP);
            format.setAlignment(CellStyle.ALIGN_CENTER);

            return format;
        }

        @Override
        public void renderGrid(RenderContext ctx, Sheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, MaxRowsExceededException
        {
            Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();

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
