/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a particular set of settings for the CrosstabTableInfo. Use this class
 * to specify the row dimensions, column dimension, measures, etc.
 *
 */
public class CrosstabSettings
{
    private TableInfo _sourceTable = null;
    private CrosstabAxis _rowAxis = new CrosstabAxis(this);
    private CrosstabAxis _colAxis = new CrosstabAxis(this);
    private List<CrosstabMeasure> _measures = new ArrayList<>();
    private String _instanceCountCaption = "Instance Count";
    private SimpleFilter _sourceTableFilter;

    /**
     * Constructs the settings object. Pass the base TableInfo as the sourceTable parameter.
     *
     * @param sourceTable The TableInfo that provides the base, un-aggregated data
     */
    public CrosstabSettings(TableInfo sourceTable)
    {
        assert null != sourceTable : "null source table passed to CrosstabTableInfo.Settings";
        _sourceTable = sourceTable;
    }

    /**
     * Specifies an option filter to be applied to the source TableInfo
     * @param sourceTableFilter the filter to be applied to the source TableInfo
     */
    public void setSourceTableFilter(SimpleFilter sourceTableFilter)
    {
        _sourceTableFilter = sourceTableFilter;
    }

    /**
     * Returns the optional SimpleFilter to be applied to the source table
     * @return The source table's simple filter
     */
    public SimpleFilter getSourceTableFilter()
    {
        return _sourceTableFilter;
    }

    /**
     * Returns the source TableInfo for the crosstab
     * @return The source TableInfo
     */
    public TableInfo getSourceTable()
    {
        return _sourceTable;
    }

    /**
     * Returns a reference to the row axis. The row axis contains all columns you want
     * to group by and display down the left of the crosstab.
     * @return The row axis
     */
    public CrosstabAxis getRowAxis()
    {
        return _rowAxis;
    }

    /**
     * Returns a reference to the column axis. The column axis contains the
     * the column who's distinct values you want to run across the top of the crosstab.
     *
     * Note that CrosstabTableInfo currently supports only one column on the column axis.
     *
     * @return A reference to the column axis.
     */
    public CrosstabAxis getColumnAxis()
    {
        return _colAxis;
    }

    /**
     * Returns a reference to the list of measures. A measure is an aggregate column,
     * defined by a source ColumnInfo (from the source TableInfo), and an aggregate function.
     *
     * You may add new CrosstabMeasure (or dervied) objects to the list returned from this method,
     * or you may use the {@link #addMeasure addMeasure()} method.
     *
     * @return The list of measures.
     */
    public List<CrosstabMeasure> getMeasures()
    {
        return _measures;
    }

    /**
     * Returns the caption to use for the instance count column. The instance count column
     * contains the number of column members in which the current row member(s) have non-null values.
     * For example, when showing a peptide down the left (rows) and experiment runs across the top (columns),
     * the instance count will contains the number of runs in which the given peptide was found.
     *
     * @return The instance count caption.
     */
    public String getInstanceCountCaption()
    {
        return _instanceCountCaption;
    }

    /**
     * Sets the instance count caption.  The instance count column
     * contains the number of column members in which the current row member(s) have non-null values.
     * For example, when showing a peptide down the left (rows) and experiment runs across the top (columns),
     * the instance count will contains the number of runs in which the given peptide was found.
     *
     * @param instanceCountCaption The new instance count caption.
     */
    public void setInstanceCountCaption(String instanceCountCaption)
    {
        _instanceCountCaption = instanceCountCaption;
    }

    /**
     * Convenience function for adding a new measure. This is equivalent to:
     * <code>
     * getMeasures().add(new CrosstabMeasure(getSourceTable(), sourceColumn, aggFunction));
     * </code>
     *
     * @param sourceColumn The field key for the source column
     * @param aggFunction The aggregate function to use
     * @return A refernece to the CrosstabMeasure object created and added to the measures list.
     */
    public CrosstabMeasure addMeasure(FieldKey sourceColumn, CrosstabMeasure.AggregateFunction aggFunction)
    {
        return addMeasure(sourceColumn, aggFunction, null);
    }

    /**
     * Same as {@link #addMeasure addMeasure(FieldKey, AggregateFunction)} except that you may also
     * specify a custom caption for the new measure.
     *
     * @param sourceColumn The field key for the source column
     * @param aggFunction The aggregate function
     * @param caption A custom caption for the new measure.
     * @return A reference to the CrosstabMeasure object created and added to the measures list.
     */
    public CrosstabMeasure addMeasure(FieldKey sourceColumn, CrosstabMeasure.AggregateFunction aggFunction, String caption)
    {
        CrosstabMeasure measure = new CrosstabMeasure(getSourceTable(), sourceColumn, aggFunction);
        if(null != caption)
            measure.setCaption(caption);
        _measures.add(measure);
        return measure;
    }

    /**
     * Returns the distinct set of ColumnInfo objects referred to by
     * the column dimension, row dimensions, and measures. Used by the {@link CrosstabTable} object.
     *
     * @return Distinct set of ColumnInfo objects
     */
    public List<ColumnInfo> getDistinctColumns()
    {
        Set<ColumnInfo> cols = new HashSet<>();
        for(CrosstabDimension dim : getColumnAxis().getDimensions())
            cols.add(dim.getSourceColumn());
        for(CrosstabDimension dim : getRowAxis().getDimensions())
            cols.add(dim.getSourceColumn());
        for(CrosstabMeasure measure : getMeasures())
            cols.add(measure.getSourceColumn());

        return new ArrayList<>(cols);
    }

    /**
     * Returns the set of ColumnInfo objects referred to by the
     * column dimension and row dimensions. Used by the {@link GroupTableInfo} object.
     *
     * @return The set of ColumnInfo objects that will group data.
     */
    public List<ColumnInfo> getGroupColumns()
    {
        List<ColumnInfo> cols = getRowAxis().getSourceColumns();
        cols.addAll(getColumnAxis().getSourceColumns());
        return cols;
    }

} //CrosstabSettings
