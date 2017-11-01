/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.visualization.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisualizationAggregateColumn;
import org.labkey.api.visualization.VisualizationSourceColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 27, 2011 11:14:20 AM
 */
public class VisualizationSourceQuery implements IVisualizationSourceQuery
{
    private Container _container;
    private UserSchema _schema;
    private String _queryName;
    private final int _uniq;
    private String _alias = null;
    private TableInfo _tinfo;
    private boolean _requireLeftJoin;
    private VisualizationSourceColumn _pivot;
    private Set<VisualizationSourceColumn> _measures = new LinkedHashSet<>();
    private Set<VisualizationSourceColumn> _selects = new LinkedHashSet<>();
    private Set<VisualizationSourceColumn> _allSelects = null;
    private Set<VisualizationAggregateColumn> _aggregates = new LinkedHashSet<>();
    private Set<VisualizationSourceColumn> _sorts = new LinkedHashSet<>();
    private IVisualizationSourceQuery _joinTarget;  // query this query must join to when building SQL
    private List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> _joinConditions;
    private List<SimpleFilter> _filters = new ArrayList<>();
    private boolean _skipVisitJoin;

    public VisualizationSourceQuery(Container container, UserSchema schema, String queryName, VisualizationSourceQuery joinTarget, int uniq)
    {
        _container = container;
        _schema = schema;
        _queryName = queryName;
        _joinTarget = joinTarget;
        _uniq = uniq;
    }

    private TableInfo getTableInfo()
    {
        if (_tinfo == null)
            _tinfo = _schema.getTable(_queryName);
        if (_tinfo == null)
        {
            throw new NotFoundException("Could not find query '" + _queryName + "' in schema '" + _schema.getName() + "'");
        }
        return _tinfo;
    }

    private void ensureSameQuery(VisualizationSourceColumn measure)
    {
        if (!measure.getSchemaName().equals(getSchemaName()) || !measure.getQueryName().equals(_queryName))
        {
            throw new IllegalArgumentException("Attempt to add measure from " + measure.getSchemaName() + "." +
                    measure.getQueryName() + " to source query " + getSchemaName() + "." + _queryName);
        }
    }

    public void setJoinConditions(List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions)
    {
        _joinConditions = joinConditions;
    }

    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinConditions()
    {
        return _joinConditions;
    }

    public Container getContainer()
    {
        return _container;
    }

    public boolean requireInnerJoin()
    {
        if (isRequireLeftJoin())
            return true;

        for (VisualizationSourceColumn col : _selects)
        {
            if (!col.isAllowNullResults())
                return true;
        }
        for (VisualizationAggregateColumn aggregate : _aggregates)
        {
            if (!aggregate.isAllowNullResults())
                return true;
        }
        return false;
    }

    public boolean isVisitTagQuery()
    {
        return _queryName.startsWith("VisualizationVisitTag");
    }

    public boolean isRequireLeftJoin()
    {
        return _requireLeftJoin;
    }

    // This can only be toggled based on the set of columns/aggregates configurations, thus it is private
    private void setRequireLeftJoin(boolean requireLeftJoin)
    {
        _requireLeftJoin = requireLeftJoin;
    }

    public void addSelect(VisualizationSourceColumn select, boolean measure)
    {
        ensureSameQuery(select);
        _selects.add(select);
        if (select.isRequireLeftJoin())
        {
            setRequireLeftJoin(true);
        }
        if (measure)
        {
            _measures.add(select);
        }
    }

    @Override
    public Set<VisualizationSourceColumn> getSelects(VisualizationSourceColumn.Factory factory, boolean includeRequiredExtraCols)
    {
        if (includeRequiredExtraCols)
        {
            if (_allSelects == null)
            {
                _allSelects = new LinkedHashSet<>(_selects);
                _allSelects.addAll(getOORColumns(factory));
                _allSelects.addAll(getDataLoggingColumns(factory));
            }
            return _allSelects;
        }

        return _selects;
    }

    public void addAggregate(VisualizationAggregateColumn aggregate)
    {
        ensureSameQuery(aggregate);
        aggregate.ensureColumn();
        _aggregates.add(aggregate);
        if (aggregate.isRequireLeftJoin())
        {
            setRequireLeftJoin(true);
        }
    }

    public Set<VisualizationAggregateColumn> getAggregates()
    {
        return _aggregates;
    }

    @Override
    public boolean contains(VisualizationSourceColumn column)
    {
        return column.getSchemaName().equals(this.getSchemaName()) && column.getQueryName().equals(this.getQueryName());
    }

    @Override
    public String getSelectListName(Set<VisualizationSourceColumn> selectAliases)
    {
        // If there is more than one available alias for a given value, just choose the first:
        return selectAliases.iterator().next().getAlias();
    }

    private static void addToColMap(Map<String, Set<VisualizationSourceColumn>> colMap, String name, VisualizationSourceColumn alias)
    {
        Set<VisualizationSourceColumn> aliases = colMap.get(name);
        if (aliases == null)
        {
            aliases = new LinkedHashSet<>();
            colMap.put(name, aliases);
        }
        aliases.add(alias);
    }


    public class PivotSourceColumn extends VisualizationSourceColumn
    {
        /** The value that's being used to pivot the data into this column */
        private String _pivotValue;
        private String _originalName;

        PivotSourceColumn(VisualizationAggregateColumn agg, Object pivotValue, String clientAlias)
        {
            super(VisualizationSourceQuery.this.getSchema(), VisualizationSourceQuery.this.getQueryName(), pivotValue.toString() + "_" + agg.getOriginalName(), true, false);
            _originalName = agg.getOriginalName();
            this._alias = pivotValue.toString() + "::" + agg.getAlias();
            if (null != agg.getLabel())
                this._label = pivotValue.toString() + " - " + agg.getLabel();
            _pivotValue = pivotValue.toString();
            // Remember the alias that the client using to refer to this column
            _clientAlias = clientAlias;
            setInNotNullSet(agg.isInNotNullSet());
        }

        @Override
        public String getOriginalName()
        {
            return _originalName;
        }

        @Override
        public Map<String, String> toJSON()
        {
            Map<String, String> result = super.toJSON();
            result.put("pivotValue", _pivotValue);
            return result;
        }
    }


    @Override
    public Map<String, Set<VisualizationSourceColumn>> getColumnNameToValueAliasMap(VisualizationSourceColumn.Factory factory, boolean measuresOnly)
    {
        Map<String, Set<VisualizationSourceColumn>> colMap = new LinkedHashMap<>();
        Set<VisualizationAggregateColumn> aggregates = getAggregates();
        if (!aggregates.isEmpty())
        {
            for (VisualizationAggregateColumn aggregate : aggregates)
            {
                if (getPivot() != null)
                {
                    // Aggregate with pivot:
                    for (Object pivotValue : getPivot().getValues())
                    {
                        VisualizationSourceColumn pivotCol = new PivotSourceColumn(aggregate, pivotValue, aggregate.getClientAlias());
                        addToColMap(colMap, pivotCol.getOriginalName(), pivotCol);
                    }
                }
                else
                {
                    // Aggregate without pivot (simple grouping)
                    addToColMap(colMap, aggregate.getOriginalName(), aggregate);
                }
            }
        }

        if (measuresOnly)
        {
            for (VisualizationSourceColumn select : _measures)
            {
                addToColMap(colMap, select.getOriginalName(), select);
            }
        }
        else
        {
            for (VisualizationSourceColumn select : getSelects(factory, true))
                addToColMap(colMap, select.getOriginalName(), select);

            if (getJoinConditions() != null)
            {
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : getJoinConditions())
                {
                    addToColMap(colMap, join.getKey().getOriginalName(), join.getKey());
                    addToColMap(colMap, join.getValue().getOriginalName(), join.getValue());
                }
            }
        }
        return colMap;
    }

    @Override
    public VisualizationSourceColumn getPivot()
    {
        return _pivot;
    }

    public void setPivot(VisualizationSourceColumn pivot)
    {
        ensureSameQuery(pivot);
        if (_pivot != null)
        {
            // See 12369
            if (_pivot.equals(pivot))
                return;
            throw new IllegalArgumentException("Can't pivot a single dataset by more than one column.  Attempt to pivot " +
                getSchemaName() + "." + _queryName + " by both " + _pivot.getSelectName() + " and " + pivot.getSelectName());
        }
        _pivot = pivot;
    }

    public void addSort(VisualizationSourceColumn sort)
    {
        ensureSameQuery(sort);
        _sorts.add(sort);
    }

    public Set<VisualizationSourceColumn> getSorts()
    {
        return _sorts;
    }

    public String getDisplayName()
    {
        return getSchemaName() + "." + _queryName;
    }


    @Override
    public String getSQLAlias()
    {
        return "\"" + getAlias() + "\"";
    }


    @Override
    public String getAlias()
    {
        if (null == _alias)
            _alias = ColumnInfo.legalNameFromName(getSchemaName() + "_" + _queryName + "_" + _uniq);
        return _alias;
    }

    public void appendColumnNames(StringBuilder sql, Set<? extends VisualizationSourceColumn> columns, boolean aggregate, boolean aliasInsteadOfName, boolean appendAlias)
    {
        if (columns == null || columns.size() == 0)
            return;
        assert !(aliasInsteadOfName && appendAlias) : "Can't both use only alias and append alias";
        String leadingSep = "";

        for (VisualizationSourceColumn column : columns)
        {
            sql.append(leadingSep);
            if (aggregate && column instanceof VisualizationAggregateColumn)
            {
                VisualizationAggregateColumn agg = (VisualizationAggregateColumn) column;
                if (agg.getAggregate().getSQLFunctionName(null) != null)
                    sql.append(agg.getAggregate().getSQLFunctionName(null)).append("(");
            }

            if (aliasInsteadOfName)
                sql.append(column.getSQLAlias());
            else
                sql.append(column.getSelectName());

            if (aggregate && column instanceof VisualizationAggregateColumn)
                sql.append(")");

            if (appendAlias)
                sql.append(" AS ").append(column.getSQLAlias()).append(" @preservetitle");
            leadingSep = ", ";
        }
    }


    public String getSelectClause(VisualizationSourceColumn.Factory factory)
    {
        return getSelectClauseHelper(factory).toString();
    }

    protected StringBuilder getSelectClauseHelper(VisualizationSourceColumn.Factory factory)
    {
        StringBuilder selectList = new StringBuilder("SELECT ");
        Set<VisualizationSourceColumn> selects = new LinkedHashSet<>();
        if (_pivot != null)
            selects.add(_pivot);
        selects.addAll(getSelects(factory, true));
        selects.addAll(_sorts);
        selects.addAll(_aggregates);
        appendColumnNames(selectList, selects, true, false, true);
        selectList.append("\n");
        return selectList;
    }

    public String getFromClause()
    {
        String schemaName = "\"" + getSchemaName() + "\"";
        String queryName = "\"" + _queryName + "\"";
        return "FROM " + schemaName + "." + queryName + "\n";
    }


    public String getGroupByClause()
    {
        if (_aggregates != null && !_aggregates.isEmpty())
        {
            StringBuilder groupBy = new StringBuilder("GROUP BY ");

            Set<VisualizationSourceColumn> groupBys = new LinkedHashSet<>();
            if (_pivot != null)
                    groupBys.add(_pivot);
            groupBys.addAll(_allSelects);
            groupBys.addAll(_sorts);

            appendColumnNames(groupBy, groupBys, false, false, false);
            groupBy.append("\n");
            return groupBy.toString();
        }
        else
            return "";
    }

    private String appendValueList(StringBuilder sql, VisualizationSourceColumn col) throws org.labkey.api.visualization.SQLGenerationException
    {
        if (col.getValues() != null && col.getValues().size() > 0)
        {
            sql.append(" IN (");
            String sep = "";
            if (col.getValues().isEmpty())
            {
                sql.append(" NULL");
            }
            else
            {
                for (Object value : col.getValues())
                {
                    sql.append(sep);
                    if (col.getType().isNumeric() || col.getType() == JdbcType.BOOLEAN)
                        sql.append(value);
                    else
                        sql.append("'").append(value).append("'");
                    sep = ", ";
                }
            }
            sql.append(")");
        }
        return sql.toString();
    }

    public String getPivotClause() throws org.labkey.api.visualization.SQLGenerationException
    {
        if (_pivot != null)
        {
            StringBuilder pivotClause = new StringBuilder("PIVOT ");
            appendColumnNames(pivotClause, _aggregates, false, true, false);
            pivotClause.append(" BY ");
            appendColumnNames(pivotClause, Collections.singleton(_pivot), false, true, false);
            appendValueList(pivotClause, _pivot);
            pivotClause.append("\n");
            return pivotClause.toString();
        }

        return "";
    }

    /**
     * Currently, query does not have a notion of dependent columns- this happens only at the query view level.  As
     * a result, it's possible to select a set of columns that will not correctly render in a data region.  One common
     * example of this occurs with out-of-range indicators, where a failure to select a column's sibling out-of-range
     * indicator column produces an error at render time.  We address this here in a nasty way by checking every selected
     * column to see if an OOR sibling column is present, and adding it to the select if so.
     * @return A set of additional columns that should be selected to ensure that the columns actually requested
     * by the user correctly display their out-of-range indicators.
     */
    private Set<VisualizationSourceColumn> getOORColumns(VisualizationSourceColumn.Factory factory)
    {
        Set<FieldKey> fieldKeys = new HashSet<>();
        for (VisualizationSourceColumn selectCol : this.getSelects(factory, false))
        {
            FieldKey oorSelect = FieldKey.fromString(selectCol.getOriginalName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX);
            fieldKeys.add(oorSelect);
        }
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getTableInfo(), fieldKeys);
        Set<VisualizationSourceColumn> oorSelects = new HashSet<>();
        for (FieldKey key : cols.keySet())
            oorSelects.add(factory.create(getSchema(), getQueryName(), key.toString(), true));
        return oorSelects;
    }

    private Map<FieldKey, ColumnInfo> getFilterColumns(SimpleFilter filter)
    {
        Map<FieldKey, String> fieldKeys = new HashMap<>();
        for (SimpleFilter.FilterClause clause : filter.getClauses())
        {
            for (FieldKey fieldKey : clause.getFieldKeys())
                fieldKeys.put(fieldKey, fieldKey.toString());
        }
        return QueryService.get().getColumns(getTableInfo(), fieldKeys.keySet());
    }

    private Set<VisualizationSourceColumn> getDataLoggingColumns(VisualizationSourceColumn.Factory factory)
    {
        // Ensure any data logging columns (such as for PHI) are present
        Set<VisualizationSourceColumn> dataLoggingColumns = new HashSet<>();
        Set<FieldKey> fieldKeys = new HashSet<>();
        for (VisualizationSourceColumn column : getSelects(factory, true))
        {
            fieldKeys.addAll(column.getColumnInfo().getColumnLogging().getDataLoggingColumns());
        }

        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getTableInfo(), fieldKeys);
        for (FieldKey key : cols.keySet())
        {
            VisualizationSourceColumn dataLoggingColumn = factory.create(getSchema(), getQueryName(), key.toString(), true);
            if (!_allSelects.contains(dataLoggingColumn))
            {
                dataLoggingColumn.setHidden(true);
                dataLoggingColumns.add(dataLoggingColumn);
            }
        }
        return dataLoggingColumns;
    }

    private String appendSimpleFilter(StringBuilder where, SimpleFilter filter, String separator)
    {
        Map<FieldKey, ColumnInfo> filterColTypes = getFilterColumns(filter);
        List<SimpleFilter.FilterClause> clauses = new ArrayList<>(filter.getClauses());
        for (SimpleFilter.FilterClause clause : clauses)
        {
            List<FieldKey> fieldKeys = clause.getFieldKeys();
            boolean allColsFound = true;
            for (FieldKey fieldKey : fieldKeys)
            {
                if (!filterColTypes.containsKey(fieldKey))
                    allColsFound = false;
            }
            if (allColsFound)
            {
                where.append(separator).append(" (").append(clause.getLabKeySQLWhereClause(filterColTypes)).append(") ");
                separator = " AND\n";
            }
            else
            {
                // Remove filter clauses for columns that are no longer found on the specified query.
                // Removing them here ensures that we send an accurate description of the current filters to the client.
                for (FieldKey fieldKey : fieldKeys)
                    filter.deleteConditions(fieldKey);
            }
        }
        return separator;
    }

    public String getWhereClause() throws org.labkey.api.visualization.SQLGenerationException
    {
        StringBuilder where = new StringBuilder();
        String sep = "WHERE ";
        for (SimpleFilter filter : _filters)
        {
            if (filter != null)
            {
                sep = appendSimpleFilter(where, filter, sep);
            }
        }
        for (VisualizationSourceColumn select : _selects)
        {
            if (select.getValues() != null && !select.getValues().isEmpty())
            {
                where.append(sep);
                appendColumnNames(where, Collections.singleton(select), false, false, false);
                appendValueList(where, select);
                sep = " AND\n";
            }
        }
        for (VisualizationSourceColumn sort : _sorts)
        {
            if (sort.getValues() != null && !sort.getValues().isEmpty() && !_selects.contains(sort))
            {
                where.append(sep);
                appendColumnNames(where, Collections.singleton(sort), false, false, false);
                appendValueList(where, sort);
                sep = " AND\n";
            }
        }
        where.append("\n");
        return where.toString();
    }

    @Override
    public String getSQL(VisualizationSourceColumn.Factory factory) throws SQLGenerationException
    {
        StringBuilder sql = new StringBuilder();
        sql.append(getSelectClause(factory)).append("\n");
        sql.append(getFromClause()).append("\n");
        sql.append(getWhereClause()).append("\n");
        sql.append(getGroupByClause()).append("\n");
        sql.append(getPivotClause()).append("\n");
        return sql.toString();
    }

    @Override
    public IVisualizationSourceQuery getJoinTarget()
    {
        return _joinTarget;
    }

    public void setJoinTarget(IVisualizationSourceQuery joinTarget)
    {
        _joinTarget = joinTarget;
    }

    public String getSchemaName()
    {
        return _schema.getSchemaName();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void addFilter(@NotNull SimpleFilter filter)
    {
        if (null != filter)
            _filters.add(filter);
    }

    public SimpleFilter[] getFilters()
    {
        return _filters.toArray(new SimpleFilter[_filters.size()]);
    }

    public boolean isSkipVisitJoin()
    {
        return _skipVisitJoin;
    }

    public void setSkipVisitJoin(boolean skipVisitJoin)
    {
        _skipVisitJoin = skipVisitJoin;
    }
}
