/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

    List colHeaders = new  ArrayList<Object>();


    //TODO: This should be an arbitrary number of groupings in any combination!!
    //TODO: Improve memory usage
    Map<Object, Map<Object, DoubleArray>> crossTab = new LinkedHashMap<Object, Map<Object, DoubleArray>>();
    Map<Object, DoubleArray> rowDatasets = new HashMap<Object, DoubleArray>();
    Map<Object, DoubleArray> colDatasets = new HashMap<Object, DoubleArray>();
    DoubleArray grandTotalDataset = new DoubleArray();

    public Crosstab(ResultSet rs, String rowField, String colField, String statField, Stats.StatDefinition stat) throws SQLException
    {
        this(rs, rowField, colField, statField, Stats.statSet(stat));
    }
    
    public Crosstab(ResultSet rs, String rowField, String colField, String statField, Set<Stats.StatDefinition> statSet) throws SQLException
    {
        this.statSet = statSet;
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
}
