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
package org.labkey.api.data;

import jxl.write.WritableSheet;
import jxl.write.WriteException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Stats;
import org.labkey.common.tools.DoubleArray;

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
    private String _rowField;
    private String _colField;
    private String _statField;
    private Map<String, ColumnInfo> _columnMap;

    List colHeaders = new  ArrayList<Object>();


    //TODO: This should be an arbitrary number of groupings in any combination!!
    //TODO: Improve memory usage
    Map<Object, Map<Object, DoubleArray>> crossTab = new LinkedHashMap<Object, Map<Object, DoubleArray>>();
    Map<Object, DoubleArray> rowDatasets = new HashMap<Object, DoubleArray>();
    Map<Object, DoubleArray> colDatasets = new HashMap<Object, DoubleArray>();
    DoubleArray grandTotalDataset = new DoubleArray();

    public Crosstab(ResultSet rs, Map<String, ColumnInfo> columnMap, String rowField, String colField, String statField, Stats.StatDefinition stat) throws SQLException
    {
        this(rs, columnMap, rowField, colField, statField, Stats.statSet(stat));
    }
    
    public Crosstab(ResultSet rs, Map<String, ColumnInfo> columnMap, String rowField, String colField, String statField, Set<Stats.StatDefinition> statSet) throws SQLException
    {
        this.statSet = statSet;
        _columnMap = columnMap;
        _rowField = rowField;
        _colField = colField;
        _statField = statField;
        String statCol = null;

        try {
            //TODO: Use DisplayField for display & value extraction. Support Grouping fields with custom groupings
            while (rs.next())
            {
                Object rowVal = null;
                DoubleArray cellValues = null;
                DoubleArray colDataset = null;
                if (null != rowField)
                {
                    rowVal = rs.getObject(rowField);
                    Map<Object, DoubleArray> rowMap = crossTab.get(rowVal);
                    if (null == rowMap)
                    {
                        rowMap = new HashMap<Object, DoubleArray>();
                        crossTab.put(rowVal, rowMap);
                        if (null == colField)
                            rowMap.put(statCol, new DoubleArray());

                        rowDatasets.put(rowVal, new DoubleArray());
                    }

                    cellValues = null;
                    colDataset = null;
                    if (null != colField)
                    {
                        Object colVal = rs.getObject(colField);
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

                Object statFieldVal = rs.getObject(statField);
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
                if (null != rowField)
                    rowDatasets.get(rowVal).add(d);
            }
        }
        finally
        {
            try { rs.close(); } catch (SQLException e) {}
        }
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

    public String getRowField()
    {
        return _rowField;
    }

    public String getColField()
    {
        return _colField;
    }

    public String getStatField()
    {
        return _statField;
    }

    public Map<String, ColumnInfo> getColumnMap()
    {
        return _columnMap;
    }

    public ExcelWriter getExcelWriter()
    {
        return new CrosstabExcelWriter(this);
    }

    public static class CrosstabExcelWriter extends ExcelWriter
    {
        private Crosstab _crosstab;

        public CrosstabExcelWriter(Crosstab crosstab)
        {
            _crosstab = crosstab;

            List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

            columns.add(new CrosstabDisplayColumn(getFieldLabel(crosstab.getRowField())));

            for (Object col : _crosstab.getColHeaders())
                columns.add(new CrosstabDisplayColumn(col));

            columns.add(new CrosstabDisplayColumn("Total", Double.class));

            setDisplayColumns(columns);
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
                    rowMap.put(getFieldLabel(_crosstab.getRowField()), String.format("%s (%s)", rowValue, rowStat.getName()));

                    ctx.setRow(mapRow(rowValue, rowStat, rowMap));
                    renderGridRow(sheet, ctx, visibleColumns);
                }
            }

            // stat totals
            for (Stats.StatDefinition rowStat : _crosstab.getStatSet())
            {
                rowMap.put(getFieldLabel(_crosstab.getRowField()), String.format("Total (%s)", rowStat.getName()));

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

        private String getFieldLabel(String fieldName)
        {
            if (null == fieldName)
                return "";

            ColumnInfo col = _crosstab.getColumnMap().get(fieldName);
            if (null != col)
                return col.getCaption();

            return fieldName;
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
