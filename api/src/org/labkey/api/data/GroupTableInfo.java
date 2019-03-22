/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryService;

import java.util.*;

/**
 * Allows group-by queries over source TableInfos, with optional aggregates
 *
 * User: Dave
 * Date: Feb 20, 2008
 * Time: 12:37:46 PM
 */
public class GroupTableInfo extends VirtualTable
{
    private final static String ALIAS = "grp";
    private final static String ALIAS_SOURCE = "src";
    private TableInfo _source;
    private List<ColumnInfo> _groupByCols;
    private List<CrosstabMeasure> _measures;
    private SimpleFilter _sourceFilter;

    public GroupTableInfo(TableInfo source, SimpleFilter sourceFilter, List<ColumnInfo> groupByCols, List<CrosstabMeasure> measures)
    {
        super(source.getSchema(), ALIAS);

        assert null != groupByCols && groupByCols.size() > 0 : "No group by columns passed to GroupTableInfo constructor!";
        assert null != measures && measures.size() > 0 : "No measures passed to GroupTableInfo constructor!";
        
        _source = source;
        _groupByCols = groupByCols;
        _measures = measures;
        _sourceFilter = sourceFilter;
        setupColumns();
    }

    public GroupTableInfo(TableInfo source, SimpleFilter filter, List<ColumnInfo> groupByCols, CrosstabMeasure... measures)
    {
        this(source, filter, groupByCols, Arrays.asList(measures));
    }

    public GroupTableInfo(TableInfo source, SimpleFilter filter, ColumnInfo[] groupByCols, CrosstabMeasure... measures)
    {
        this(source, filter, Arrays.asList(groupByCols), Arrays.asList(measures));
    }

    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        SQLFragment sql = new SQLFragment("SELECT ");
        String sep = "";
        
        //add the group-by columns
        for(ColumnInfo col : getGroupByColumns())
        {
            sql.append(sep);
            sql.append(ALIAS_SOURCE);
            sql.append(".");
            sql.append(col.getAlias());
            sep = ",\n";
        }

        //add the measures
        for(CrosstabMeasure measure : getMeasures())
        {
            sql.append(sep);
            sql.append(measure.getSqlExpression(ALIAS_SOURCE));
            sql.append(" AS ");
            sql.append(AggregateColumnInfo.getColumnName(null, measure));
        }

        //from the source table select
        sql.append("\nFROM (\n");
        TableInfo source = getSourceTable();

        sql.append(QueryService.get().getSelectSQL(source, getDistinctColumns(), _sourceFilter, null, Table.ALL_ROWS, Table.NO_OFFSET, false));
        sql.append("\n) AS ");
        sql.append(ALIAS_SOURCE);

        //group by
        sql.append("\nGROUP BY ");
        sep = "";
        for(ColumnInfo col : getGroupByColumns())
        {
            sql.append(sep);
            sql.append(ALIAS_SOURCE);
            sql.append(".");
            sql.append(col.getAlias());
            sep = ",\n";
        }

        return sql;
    }

    public TableInfo getSourceTable()
    {
        return _source;
    }

    public List<ColumnInfo> getGroupByColumns()
    {
        return _groupByCols;
    }

    protected List<ColumnInfo> getDistinctColumns()
    {
        Set<ColumnInfo> cols = new HashSet<>();
        for(ColumnInfo col : getGroupByColumns())
            cols.add(col);
        for(CrosstabMeasure measure : getMeasures())
            cols.add(measure.getSourceColumn());

        return new ArrayList<>(cols);
    }

    public List<CrosstabMeasure> getMeasures()
    {
        return _measures;
    }

    protected void setupColumns()
    {
        //group-by columns
        for(ColumnInfo col : getGroupByColumns())
            addColumn(createGroupByColumn(col));

        //aggregate columns
        for(CrosstabMeasure measure : getMeasures())
            addColumn(createAggregateColumn(measure));
    }

    protected ColumnInfo createGroupByColumn(ColumnInfo sourceCol)
    {
        // All lookup columns have already been flattened into a subselect for us, so we just need to pluck out the
        // values by alias
        ExprColumn result = new ExprColumn(this, sourceCol.getAlias(), new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + sourceCol.getAlias()), sourceCol.getJdbcType());
        result.copyAttributesFrom(sourceCol);
        return result;
    }

    protected ColumnInfo createAggregateColumn(CrosstabMeasure measure)
    {
        return new AggregateColumnInfo(this, null, measure);
    }
}
