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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisDataRequest;
import org.labkey.api.visualization.VisualizationAggregateColumn;
import org.labkey.api.visualization.VisualizationIntervalColumn;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.visualization.VisualizationController;
import org.labkey.visualization.test.VisTestSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Date: Jan 25, 2011 4:27:46 PM
 */
public class VisualizationSQLGenerator implements HasViewContext
{
    private Map<String, VisualizationSourceQuery> _sourceQueries = new LinkedHashMap<>();
    private Map<String, VisualizationIntervalColumn> _intervals = new HashMap<>();
    private List<VisualizationSourceColumn> _groupBys = new ArrayList<>();
    private Set<VisualizationSourceColumn> _whereNotNulls = new LinkedHashSet<>();
    private Set<VisualizationSourceColumn> _pivots = new LinkedHashSet<>();
    private Map<FieldKey, Set<String>> _allFilters = new LinkedHashMap<>();
    private Map<FieldKey, ColumnInfo> _filterColTypes = new LinkedHashMap<>();

    private ViewContext _viewContext;
    private VisualizationSourceColumn.Factory _columnFactory = new VisualizationSourceColumn.Factory();
    private boolean _metaDataOnly;
    private boolean _joinToFirst;
    private Integer _limit;

    public VisualizationSQLGenerator()
    {

    }

    public VisualizationSQLGenerator(ViewContext context, VisDataRequest req)
    {
        setViewContext(context);
        fromVisDataRequest(req);
    }


    @Override
    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    @Override
    public ViewContext getViewContext()
    {
        return _viewContext;
    }


    private Map<String, VisualizationProvider> _providers = new CaseInsensitiveHashMap<>();

    public VisualizationProvider ensureVisualizationProvider(String schemaName, VisualizationProvider.ChartType type)
    {
        VisualizationProvider provider = _providers.get(schemaName);
        if (provider == null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(_viewContext.getUser(), _viewContext.getContainer(), schemaName);
            if (userSchema == null)
            {
                throw new IllegalArgumentException("No such schema: " + schemaName);
            }
            provider = userSchema.createVisualizationProvider();
            if (provider == null)
            {
                throw new IllegalArgumentException("No provider available for schema: " + userSchema.getSchemaPath());
            }
            provider.configure(type);
            _providers.put(schemaName, provider);
        }
        else
        {
            if (type != provider.getType())
                throw new IllegalArgumentException("Cannot generate SQL for two chart types with a single call.  Chart type was " + provider.getType() + ", but is now requested as " + type);
        }
        return provider;
    }

    public @NotNull VisualizationProvider getVisualizationProvider(String schema)
    {
        VisualizationProvider provider = _providers.get(schema);
        if (provider == null)
            throw new IllegalStateException("No provider configured for schema " + schema);
        return provider;
    }


    public void fromVisDataRequest(VisDataRequest visDataRequest)
    {
        try
        {
            _fromVisDataRequest(visDataRequest);
        }
        catch (IllegalArgumentException x)
        {
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw x;
        }
    }

    private void _fromVisDataRequest(VisDataRequest visDataRequest)
    {
        _metaDataOnly = visDataRequest.isMetaDataOnly();
        _joinToFirst = visDataRequest.isJoinToFirst();

        List<VisDataRequest.MeasureInfo> measureInfos = visDataRequest.getMeasures();
        if (measureInfos != null && !measureInfos.isEmpty())
        {
            VisualizationSourceQuery previous = null;
            for (VisDataRequest.MeasureInfo measureInfo : measureInfos)
            {
                VisDataRequest.Measure measure = measureInfo.getMeasure();
                if (null == measure || measure.isEmpty())
                {
                    throw new IllegalArgumentException("The 'measure' property is required for each of the elements in the measures array.");
                }

                VisDataRequest.Measure dimension = measureInfo.getDimension();

                VisualizationSourceQuery query;
                VisualizationSourceColumn measureCol;
                if (null != dimension && !dimension.isEmpty())
                {
                    // this column is the value column of a pivot, so we assume that it's an aggregate
                    measureCol = new VisualizationAggregateColumn(getViewContext(), measure);
                    query = ensureSourceQuery(_viewContext.getContainer(), measureCol, previous);
                    query.addAggregate((VisualizationAggregateColumn) measureCol);
                    VisualizationSourceColumn pivot = _columnFactory.create(getViewContext(), dimension);
                    query.setPivot(pivot);
                    _pivots.add(pivot);
                }
                else
                {
                    measureCol = _columnFactory.create(getViewContext(), measure);
                    query = ensureSourceQuery(_viewContext.getContainer(), measureCol, previous);
                    query.addSelect(measureCol, true);

                    if (measure.isDemographic())
                        query.setSkipVisitJoin(measure.isDemographic());
                }

                String timeAxis = measureInfo.getTime();
                VisualizationProvider.ChartType type;
                if (timeAxis != null)
                {
                    if ("date".equalsIgnoreCase(timeAxis))
                        type = VisualizationProvider.ChartType.TIME_DATEBASED;
                    else if ("visit".equalsIgnoreCase(timeAxis))
                        type = VisualizationProvider.ChartType.TIME_VISITBASED;
                    else
                        throw new IllegalArgumentException("Unknown time value: " + timeAxis);
                }
                else
                    throw new IllegalArgumentException("Only time charts are currently supported: expected 'time' property on each measure.");

                VisualizationProvider provider = ensureVisualizationProvider(query.getSchemaName(), type);
                VisualizationIntervalColumn newInterval = null;
                switch (type)
                {
                    case TIME_DATEBASED:
                        VisDataRequest.DateOptions dateOptions = measureInfo.getDateOptions();

                        if (dateOptions != null)
                        {
                            String interval = dateOptions.getInterval();
                            interval = normalizeInterval(interval);

                            VisDataRequest.Measure dateProperties = dateOptions.getDateCol();
                            VisDataRequest.Measure zeroDateProperties = dateOptions.getZeroDateCol();

                            if (dateProperties != null && zeroDateProperties != null)
                            {
                                VisualizationSourceColumn dateCol = _columnFactory.create(getViewContext(), dateProperties);
                                dateCol.setAllowNullResults(measureCol.isAllowNullResults());
                                dateCol.setInNotNullSet(measureCol.isInNotNullSet());
                                ensureSourceQuery(_viewContext.getContainer(), dateCol, query).addSelect(dateCol, false);

                                VisualizationSourceColumn zeroDateCol = _columnFactory.create(getViewContext(), zeroDateProperties);
                                zeroDateCol.setAllowNullResults(false);
                                ensureSourceQuery(_viewContext.getContainer(), zeroDateCol, query).addSelect(zeroDateCol, false);

                                newInterval = new VisualizationIntervalColumn(zeroDateCol, dateCol, interval, false);
                            }
                            else if (dateOptions.isZeroDayVisitTagSet())
                            {
                                VisualizationSourceColumn zeroDayCol = null;
                                boolean useProtocolDay = dateOptions.isUseProtocolDay();

                                //  Issue 20459: handle 'Unaligned' (i.e. null zero day) case for calculating weeks/months
                                if (null != dateOptions.getZeroDayVisitTag())
                                {
                                    zeroDayCol = _columnFactory.create(getPrimarySchema(), "VisualizationVisitTag", "ZeroDay", false, dateOptions);
                                    ensureSourceQuery(_viewContext.getContainer(), zeroDayCol, query).addSelect(zeroDayCol, false);
                                }

                                newInterval = new VisualizationIntervalColumn(zeroDayCol, measureCol, interval, true);
                            }
                            else
                            {
                                throw new IllegalArgumentException("The 'zeroDayVisitTag' property or the 'dateCol' and 'zeroDateCol' properties are required.");
                            }

                            if (interval != null)
                            {
                                boolean foundMatch = false;
                                if (!_intervals.isEmpty())
                                {
                                    for (VisualizationIntervalColumn existingInterval : _intervals.values())
                                    {
                                        if (existingInterval.getStartCol() == newInterval.getStartCol() && existingInterval.getInterval() == newInterval.getInterval())
                                        {
                                            foundMatch = true;
                                        }
                                    }
                                    if (!foundMatch)
                                    {
                                        throw new IllegalArgumentException("Multiple intervals with different start dates or units are not supported");
                                    }
                                }
                                else
                                {
                                    _intervals.put(newInterval.getFullAlias(), newInterval);
                                }
                            }
                        }

                        break;
                    case TIME_VISITBASED:
                        // No special handling needed for visit-based charts
                        break;
                }

                for (String q : measureInfo.getFilterArray())
                {
                    if (null != q)
                    {
                        SimpleFilter filter = SimpleFilter.createFilterFromParameter(q);
                        if (filter != null)
                        {
                            FieldKey key = FieldKey.fromParts(newInterval != null ? newInterval.getFullAlias() : measureCol.getAlias());

                            // issue 21601: can't apply pivot query filters to outer query, they should be applied within the pivot query
                            if (query.getPivot() != null)
                            {
                                filter = SimpleFilter.createFilterFromParameter(q);
                                if (filter != null)
                                    query.addFilter(filter);
                            }
                            else
                            {
                                if (!_allFilters.containsKey(key))
                                {
                                    _allFilters.put(key, new LinkedHashSet<>());
                                    _filterColTypes.put(key, measureCol.getColumnInfo());
                                }
                                _allFilters.get(key).add(q);
                            }
                        }
                    }
                }

                provider.addExtraSelectColumns(_columnFactory, query);

                if (previous == null || !isJoinToFirst())
                    previous = query;
            }
        }

        List<VisDataRequest.Measure> sorts = visDataRequest.getSorts();
        if (null != sorts && !sorts.isEmpty())
        {
            for (VisDataRequest.Measure sortInfo : sorts)
            {
                VisualizationSourceColumn sort = _columnFactory.create(getViewContext(), sortInfo);
                getSourceQuery(sort, true).addSort(sort);
            }
        }

        String filterUrlString = visDataRequest.getFilterUrl();
        if (filterUrlString != null)
        {
            ActionURL filterUrl = new ActionURL(filterUrlString);
            String queryName = visDataRequest.getFilterQuery();
            VisualizationSourceQuery query = _sourceQueries.get(queryName);
            if (query != null)
            {
                String dataRegionName = filterUrl.getParameter(QueryParam.dataRegionName);
                if (dataRegionName == null)
                    dataRegionName = VisualizationController.FILTER_DATAREGION;

                SimpleFilter filter = new SimpleFilter(filterUrl, dataRegionName);
                query.addFilter(filter);
            }
        }

        List<VisDataRequest.Measure> groupBys = visDataRequest.getGroupBys();
        if (groupBys != null)
        {
            _groupBys.addAll(groupBys.stream()
                    .map(additionalSelectInfo -> _columnFactory.create(getViewContext(), additionalSelectInfo))
                    .collect(Collectors.toList()));
        }

        ensureJoinColumns();

        Integer limit = visDataRequest.getLimit();
        if (limit != null)
        {
            _limit = limit;
        }

        if (_sourceQueries.isEmpty())
        {
            throw new IllegalArgumentException("No source queries requested with the specified measures array.");
        }
    }

    private void ensureJoinColumns()
    {
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            IVisualizationSourceQuery possibleJoinTarget = query.getJoinTarget();
            if (possibleJoinTarget != null)
            {
                if (!(possibleJoinTarget instanceof VisualizationSourceQuery))
                    throw new IllegalStateException("Expected VisualizationSourceQuery instance, found " + possibleJoinTarget.getClass().getName());
                VisualizationSourceQuery joinTarget = (VisualizationSourceQuery) possibleJoinTarget;
                if (!joinTarget.getSchemaName().equalsIgnoreCase(query.getSchemaName()))
                {
                    throw new IllegalArgumentException("Cross-schema joins are not yet supported.  Attempt to join " +
                            query.getDisplayName() + " to " + joinTarget.getDisplayName());
                }
                VisualizationProvider<?> provider = getVisualizationProvider(query.getSchemaName());
                List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions = new ArrayList<>();
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : provider.getJoinColumns(_columnFactory, query, joinTarget, false))
                {
                    // Make sure we're selecting all the columns we need to join on:
                    VisualizationSourceColumn left = join.getKey();
                    getSourceQuery(join.getKey(), true).addSelect(left, false);
                    VisualizationSourceColumn right = join.getValue();
                    getSourceQuery(join.getValue(), true).addSelect(right, false);
                    // We need to filter both left and right queries by the same values (for the same columns), since we
                    // may be doing an outer join.
                    left.syncValues(right);
                    joinConditions.add(new Pair<>(left, right));
                }
                query.setJoinConditions(joinConditions);
            }
        }
    }

    private VisualizationSourceQuery getSourceQuery(VisualizationSourceColumn column, boolean assertPresent)
    {
        String queryName = column.getSchemaName() + "." + column.getQueryName();
        VisualizationSourceQuery query = _sourceQueries.get(queryName);
        if (query == null && assertPresent)
            throw new IllegalStateException("Expected query " + queryName + " to be previously registered.");
        return query;
    }

    private VisualizationSourceQuery ensureSourceQuery(Container container, VisualizationSourceColumn column, VisualizationSourceQuery joinQuery)
    {
        String queryName = column.getSchemaName() + "." + column.getQueryName();
        VisualizationSourceQuery query = _sourceQueries.get(queryName);
        if (query == null)
        {
            int counter = _sourceQueries.size() + 1;
            query = new VisualizationSourceQuery(container, column.getSchema(), column.getQueryName(), joinQuery, counter);
            _sourceQueries.put(queryName, query);
        }
        return query;
    }

    public String getSQL() throws SQLGenerationException
    {
        Set<IVisualizationSourceQuery> outerJoinQueries = new LinkedHashSet<>();
        Set<VisualizationSourceQuery> innerJoinQueries = new LinkedHashSet<>();
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            if (query.requireInnerJoin())
                innerJoinQueries.add(query);
            else
                outerJoinQueries.add(query);
        }

        // Create a single, nested outer join query to gather up all the outer-join results
        List<IVisualizationSourceQuery> queries = new ArrayList<>();
        IVisualizationSourceQuery nestedOuterJoinQuery = null;
        if (outerJoinQueries.size() > 1)
        {
            nestedOuterJoinQuery = new OuterJoinSourceQuery(this, outerJoinQueries, _limit != null);
            queries.add(nestedOuterJoinQuery);
        }
        else
            queries.addAll(outerJoinQueries);

        if (nestedOuterJoinQuery != null)
        {
            // Remap any inner join queries to join to the outer-join inner query above:
            for (VisualizationSourceQuery innerJoinQuery : innerJoinQueries)
            {
                IVisualizationSourceQuery joinTarget = innerJoinQuery.getJoinTarget();
                if (outerJoinQueries.contains(joinTarget))
                    innerJoinQuery.setJoinTarget(nestedOuterJoinQuery);
            }
        }
        // Add inner joins to the inner-join queries
        queries.addAll(innerJoinQueries);

        String sql = getSubselectSQL(null, _columnFactory, queries, new ArrayList<>(_intervals.values()), "INNER JOIN", _groupBys.isEmpty(), _limit != null, true);

        if (!_groupBys.isEmpty())
        {
            IVisualizationSourceQuery joinQuery = innerJoinQueries.isEmpty() ? outerJoinQueries.iterator().next() : innerJoinQueries.iterator().next();
            sql = wrapInGroupBy(joinQuery, queries, sql);
        }

        if (_limit != null)
        {
            sql = sql + " LIMIT " + _limit;
        }

        return sql;
    }


    private String wrapInGroupBy(IVisualizationSourceQuery joinQuery, List<IVisualizationSourceQuery> queries, String sql)
            throws SQLGenerationException
    {
        Map<String, Set<VisualizationSourceColumn>> columnAliases = getColumnMapping(_columnFactory, queries);

        StringBuilder aggregatedSQL = new StringBuilder("SELECT ");
        String separator;
        Set<VisualizationSourceQuery> groupByQueries = new LinkedHashSet<>();
        StringBuilder groupByAndSelectSQL = new StringBuilder();

        for (VisualizationSourceColumn groupByColumn : _groupBys)
        {
            VisualizationSourceQuery groupByQuery = ensureSourceQuery(_viewContext.getContainer(), groupByColumn, null);
            groupByQuery.addSelect(groupByColumn, false);
            groupByQueries.add(groupByQuery);
        }
        if (_intervals.size() > 1)
        {
            throw new IllegalArgumentException("A maximum of one interval is supported");
        }

        for (VisualizationSourceQuery groupByQuery : groupByQueries)
        {
            groupByQuery.appendColumnNames(groupByAndSelectSQL, groupByQuery.getSelects(_columnFactory, false), false, false, false);
        }

        for (VisualizationProvider provider : _providers.values())
        {
            provider.appendAggregates(groupByAndSelectSQL, columnAliases, _intervals, "x", joinQuery);
        }

        aggregatedSQL.append(groupByAndSelectSQL);
        aggregatedSQL.append(", COUNT(*) AS AggregateCount \n");
        for (IVisualizationSourceQuery query : queries)
        {
            // Get all of the columns selected for all of the measures, even if they've been pivoted out into separate
            // columns based on a dimension
            for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : query.getColumnNameToValueAliasMap(_columnFactory, true).entrySet())
            {
                for (VisualizationSourceColumn col : entry.getValue())
                {
                    String alias = col.getAlias();
                    aggregatedSQL.append(", AVG(CAST(x.\"").append(alias).append("\" AS DOUBLE)) AS \"").append(alias).append("\"")
                            .append(", STDDEV(x.\"").append(alias).append("\") AS \"").append(alias).append("_STDDEV\"")
                            .append(", STDERR(x.\"").append(alias).append("\") AS \"").append(alias).append("_STDERR\"")
                            .append("\n");
                }
            }
        }

        aggregatedSQL.append("\n FROM (").append(sql).append(") x");

        for (VisualizationSourceQuery groupByQuery : groupByQueries)
        {
            aggregatedSQL.append(" INNER JOIN (SELECT * FROM ");
            aggregatedSQL.append(groupByQuery.getSchemaName());
            aggregatedSQL.append(".");
            aggregatedSQL.append(groupByQuery.getQueryName());
            aggregatedSQL.append("\n");
            aggregatedSQL.append(groupByQuery.getWhereClause());
            aggregatedSQL.append(") AS ");
            aggregatedSQL.append(groupByQuery.getSQLAlias());
            separator = "";
            aggregatedSQL.append(" ON ");
            VisualizationProvider<?> provider = getVisualizationProvider(groupByQuery.getSchemaName());
            for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> pair : provider.getJoinColumns(_columnFactory, groupByQuery, joinQuery, true))
            {
                aggregatedSQL.append(separator);
                separator = " AND ";
                aggregatedSQL.append("x.");
                VisualizationSourceColumn vsc = (VisualizationSourceColumn)pickFirst(columnAliases.get(pair.getKey().getOriginalName()));
                aggregatedSQL.append(vsc.getSQLAlias());
                aggregatedSQL.append(" = ");
                aggregatedSQL.append(groupByQuery.getSQLAlias());
                aggregatedSQL.append(".\"");
                aggregatedSQL.append(pair.getValue().getOriginalName());
                aggregatedSQL.append("\"");
            }
        }

        aggregatedSQL.append("\nGROUP BY ");
        aggregatedSQL.append(groupByAndSelectSQL);

        // For now, always sort by the GROUP BY columns - the ones requested by the client with the "groupBys" parameter,
        // and the interval columns. The sorts parameter is ignored in the aggregation case.
        // We should use the sort parameter instead, but it makes SQL generation much harder
        aggregatedSQL.append("\nORDER BY ");
        aggregatedSQL.append(groupByAndSelectSQL);
        if (findSchema(_groupBys).getDbSchema().getSqlDialect().isSqlServer() && _limit == null)
            aggregatedSQL.append(" LIMIT 1000000");

        return aggregatedSQL.toString();
    }

    @Nullable
    private static IVisualizationSourceQuery findQuery(VisualizationSourceColumn column, Collection<IVisualizationSourceQuery> queries)
    {
        for (IVisualizationSourceQuery query : queries)
        {
            if (query.contains(column))
                return query;
        }
        return null;
    }

    public String getSubselectSQL(IVisualizationSourceQuery parentQuery, VisualizationSourceColumn.Factory factory,
                                  Collection<IVisualizationSourceQuery> queries, List<VisualizationIntervalColumn> intervals,
                                  String joinOperator, boolean includeOrderBys, boolean hasRowLimit) throws SQLGenerationException
    {
        return getSubselectSQL(parentQuery, factory, queries, intervals, joinOperator, includeOrderBys, hasRowLimit, false);
    }

    private String getSubselectSQL(IVisualizationSourceQuery parentQuery, VisualizationSourceColumn.Factory factory,
                                   Collection<IVisualizationSourceQuery> queries, List<VisualizationIntervalColumn> intervals,
                                   String joinOperator, boolean includeOrderBys, boolean hasRowLimit, boolean isOuterSelect) throws SQLGenerationException
    {
        // Reorder the queries in case one can join to the other, but not the reverse. For example,
        // we can join from a standard participant visit/date dataset to a demographic dataset, but not the reverse.
        List<IVisualizationSourceQuery> reorderedQueries = new ArrayList<>();
        for (IVisualizationSourceQuery query : queries)
        {
            if (query.getJoinTarget() == null)
            {
                reorderedQueries.add(0, query);
            }
            else
            {
                reorderedQueries.add(query);
            }
        }
        queries = reorderedQueries;

        // Now that we have the full list of columns we want to select, we can generate our select list
        Map<String, Set<VisualizationSourceColumn>> allAliases = new LinkedHashMap<>();
        for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : getColumnMapping(factory, queries).entrySet())
        {
            if (entry.getValue().size() > 1)
            {
                boolean isJoinColumn = true;
                for (VisualizationSourceColumn select : entry.getValue())
                {
                    VisualizationProvider provider = getVisualizationProvider(select.getSchemaName());
                    if (!provider.isJoinColumn(select, getViewContext().getContainer()))
                    {
                        isJoinColumn = false;
                        break;
                    }
                }

                if (isJoinColumn)
                    allAliases.put(entry.getKey(), entry.getValue());
                else
                {
                    // if the select is not a join column, we key the column map by the alias so
                    // that the columns don't get coalesced in the outer select
                    for (VisualizationSourceColumn select : entry.getValue())
                        allAliases.put(select.getAlias(), Collections.singleton(select));
                }
            }
            else
                allAliases.put(entry.getKey(), entry.getValue());
        }

        StringBuilder masterSelectList = new StringBuilder();
        String sep = "";

        for (Map.Entry<String,Set<VisualizationSourceColumn>> entry : allAliases.entrySet())
        {
            Set<VisualizationSourceColumn> selectAliases = entry.getValue();

            // issue 21601: if the pivot column is also requested as a measure, we don't want to select it in the masterSelectList
            // but we do want to have each PivotSourceColumn added to the whereNotNulls set
            if (!_pivots.isEmpty())
            {
                boolean isPivotCol = false;
                for (VisualizationSourceColumn col : selectAliases)
                {
                    if (_pivots.contains(col))
                    {
                        isPivotCol = true;
                        break;
                    }

                    if (col instanceof VisualizationSourceQuery.PivotSourceColumn && col.isInNotNullSet())
                    {
                        _whereNotNulls.add(col);
                    }
                }

                if (isPivotCol)
                    continue;
            }

            String selectAlias;
            if (parentQuery != null)
                selectAlias = parentQuery.getSelectListName(selectAliases);
            else
            {
                VisualizationSourceColumn col = (VisualizationSourceColumn)pickFirst(selectAliases);
                String label = col.getLabel();
                selectAlias = col.getSQLAlias() + (null == label ? " @preservetitle" : " @title='" + StringUtils.replace(label,"'", "''") + "'");
                if (col.isHidden())
                    selectAlias += " @hidden";

                // stay outside the recursion, isOuterSelect only true at top level
                if (isOuterSelect && col.isInNotNullSet())
                    _whereNotNulls.add(col);
            }
            masterSelectList.append(sep).append(selectAlias);
            sep = ",\n\t";
        }

        int intervalsSize = intervals.size();
        for (VisualizationIntervalColumn interval : intervals)
        {
            // if the end date has multiple aliases, set it to be the first
            Set<VisualizationSourceColumn> intervalAliases = allAliases.get(interval.getEndCol().getOriginalName());
            if (intervalAliases != null && intervalsSize > 1)
            {
                interval.getEndCol().setOtherAlias(factory.getByAlias(intervalAliases.iterator().next().getAlias()).getAlias());
            }
            masterSelectList.append(sep).append(interval.getSQL()).append(" AS ").append(interval.getSQLAlias(intervalsSize)).append(" @preservetitle");
        }

        Map<VisualizationSourceColumn, IVisualizationSourceQuery> orderBys = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder();
        for (IVisualizationSourceQuery query : queries)
        {
            for (VisualizationSourceColumn orderBy : query.getSorts())
            {
                Set<VisualizationSourceColumn> orderByAliases = allAliases.get(orderBy.getOriginalName());

                VisualizationSourceColumn column;
                if (orderByAliases != null && orderByAliases.size() > 1)
                {
                    column = factory.getByAlias(orderByAliases.iterator().next().getAlias());
                }
                else
                {
                    column = orderBy;
                }

                if (!orderBys.containsKey(column))
                {
                    orderBys.put(orderBy, query);
                }
            }

            if (sql.length() == 0)
            {
                sql.append("SELECT ").append(masterSelectList).append(" FROM\n");
            }
            else
            {
                sql.append("\n").append(query.isRequireLeftJoin() ? "LEFT JOIN" : joinOperator).append("\n");
            }

            String querySql = query.getSQL(factory);
            sql.append("(").append(querySql).append(") AS ").append(query.getSQLAlias()).append("\n");
            if (query.getJoinTarget() != null)
            {
                sql.append("ON ");
                String andSep = "";

                VisualizationSourceColumn leftColumn;
                IVisualizationSourceQuery leftQuery;
                VisualizationSourceColumn rightColumn;
                IVisualizationSourceQuery rightQuery;

                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> condition : query.getJoinConditions())
                {
                    // either left or right should be our current query
                    leftColumn = condition.getKey();
                    leftQuery = findQuery(leftColumn, queries);
                    rightColumn = condition.getValue();
                    rightQuery = findQuery(rightColumn, queries);


                    if (leftQuery == null)
                    {
                        throw new SQLGenerationException("Unable to determine query for left column: " + leftColumn.getAlias());
                    }

                    if (rightQuery == null)
                    {
                        throw new SQLGenerationException("Unable to determine query for right column: " + rightColumn.getAlias());
                    }

                    // issue 20526: inner join gets confused about which alias to use for the join condition after coalesce
                    String rightAlias = parentQuery != null ? rightColumn.getSQLAlias() : rightColumn.getSQLOther();

                    sql.append(andSep);
                    andSep = " AND ";
                    sql.append(leftQuery.getSQLAlias()).append(".").append(leftColumn.getSQLAlias()).append(" = ");
                    sql.append(rightQuery.getSQLAlias()).append(".").append(rightAlias).append("\n");
                }
            }
        }

        if (isOuterSelect)
        {
            sql.append(getWhereClause());
        }

        if (includeOrderBys && !orderBys.isEmpty())
        {
            sql.append(getOrderByClause(orderBys, allAliases, hasRowLimit));
        }

        return sql.toString();
    }

    private String getOrderByClause(Map<VisualizationSourceColumn, IVisualizationSourceQuery> orderBys,
                                    Map<String, Set<VisualizationSourceColumn>> allAliases,
                                    boolean hasRowLimit)
    {
        if (orderBys.isEmpty())
        {
            return "";
        }
        String sep = "";
        StringBuilder sql = new StringBuilder("ORDER BY ");
        for (Map.Entry<VisualizationSourceColumn, IVisualizationSourceQuery> orderBy : orderBys.entrySet())
        {
            String queryName = orderBy.getValue().getSQLAlias();
            String aliasName = orderBy.getKey().getSQLAlias();

            // Issue 28529: Time chart sort order issue when having multiple visit based measures that don't overlap on all visits
            String visitSequenceNumSuffix = "/Visit/SequenceNumMin";
            if (orderBy.getKey().getOriginalName().endsWith(visitSequenceNumSuffix))
            {
                String newVisitSequenceNum = orderBy.getKey().getOriginalName().replace(visitSequenceNumSuffix, "/sequencenum");
                Set<VisualizationSourceColumn> alternateSequeceNumCol = allAliases.get(newVisitSequenceNum);
                if (alternateSequeceNumCol != null && alternateSequeceNumCol.size() > 1)
                    aliasName = alternateSequeceNumCol.iterator().next().getSQLAlias();
            }

            sql.append(sep).append(queryName).append(".").append(aliasName);
            sep = ", ";
        }
        if (findSchema(orderBys.keySet()).getDbSchema().getSqlDialect().isSqlServer() && !hasRowLimit)
            sql.append(" LIMIT 1000000");
        return sql.toString();
    }

    private String getWhereClause()
    {
        if (_whereNotNulls.isEmpty() && _allFilters.isEmpty())
        {
            return "";
        }

        String sep = "";
        String outerSep = "";
        StringBuilder sql = new StringBuilder(" WHERE ");

        if (!_whereNotNulls.isEmpty())
        {
            sql.append("(");
            for (VisualizationSourceColumn notNull : _whereNotNulls)
            {
                sql.append(sep).append(notNull.getSQLAlias());
                sql.append(" IS NOT NULL ");
                sep = "\nOR ";
            }
            sql.append(")");
            outerSep = " AND ";
        }

        if (!_allFilters.isEmpty())
        {
            sep = "";
            for (FieldKey key : _allFilters.keySet())
            {
                for (String queryString : _allFilters.get(key))
                {
                    String column = SimpleFilter.getColumnFromParameter(queryString);
                    if (null != column)
                    {
                        String keyQueryString = queryString.replace(column, key.toString());
                        SimpleFilter keyFilter = SimpleFilter.createFilterFromParameter(keyQueryString);

                        if (null != keyFilter)
                        {
                            for (SimpleFilter.FilterClause clause : keyFilter.getClauses())
                            {
                                String clauseStr = clause.getLabKeySQLWhereClause(_filterColTypes);
                                if (_intervals.containsKey(key.toString()))
                                {
                                    // issue 21852: replace the interval key with the calculation
                                    VisualizationIntervalColumn intervalCol = _intervals.get(key.toString());
                                    clauseStr = clauseStr.replace("\"" + key.toString() + "\"", "(" + intervalCol.getSQL() + ")");
                                }

                                sql.append(outerSep).append(sep).append(" (").append(clauseStr).append(") ");
                                outerSep = "";
                                sep = " AND\n";
                            }
                        }
                    }
                }
            }
        }

        return sql.toString();
    }

    public Sort getSort()
    {
        Sort sort = new Sort();

        // see comment for ORDER BY sql in wrapInGroupBy (i.e. include group bys in sorting)
        for (VisualizationSourceColumn groupBy : _groupBys)
        {
            sort.appendSortColumn(FieldKey.fromParts(groupBy.getOriginalName()), Sort.SortDirection.ASC, true);
        }

        Map<String, VisualizationSourceColumn> sorts = new LinkedHashMap<>();
        for (IVisualizationSourceQuery query : _sourceQueries.values())
        {
            for (VisualizationSourceColumn orderBy : query.getSorts())
            {
                sorts.put(orderBy.getAlias(), orderBy);
            }
        }
        for (VisualizationSourceColumn sortCol : sorts.values())
        {
            sort.appendSortColumn(sortCol.getAlias(), true);
        }
        return sort;
    }

    private static UserSchema findSchema(Collection<VisualizationSourceColumn> columns)
    {
        for (VisualizationSourceColumn column : columns)
        {
            if (column.getSchema() != null)
            {
                return column.getSchema();
            }
        }
        throw new IllegalArgumentException("At least one column should have a schema");
    }


    public List<VisualizationSourceColumn> getColumns()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<>(_sourceQueries.values());
        Map<String, Set<VisualizationSourceColumn>> allAliases = getColumnMapping(_columnFactory, queries);
        Set<VisualizationSourceColumn> result = new LinkedHashSet<>();

        // The default column mapping references the first available valid alias:
        for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : allAliases.entrySet())
        {
            result.addAll(entry.getValue().stream().collect(Collectors.toList()));
        }

        return new ArrayList<>(result);
    }


    public List<Map<String, String>> getColumnAliases()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<>(_sourceQueries.values());
        Map<String, Set<VisualizationSourceColumn>> allAliases = getColumnMapping(_columnFactory, queries);
        Set<Map<String, String>> result = new LinkedHashSet<>();

        // The default column mapping references the first available valid alias:
        for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : allAliases.entrySet())
        {
            Set<VisualizationSourceColumn> columnSet = entry.getValue();
            result.add(columnSet.iterator().next().toJSON());

            if (columnSet.size() > 1)
            {
                for (VisualizationSourceColumn select : columnSet)
                {
                    VisualizationProvider provider = getVisualizationProvider(select.getSchemaName());
                    if (!provider.isJoinColumn(select, getViewContext().getContainer()))
                        result.add(select.toJSON());
                }
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * This method and its usage should go away as soon as the client has been migrated to use the columnAliases JSON
     * values, since they contain everything this mapping does and more.
     */
    @Deprecated
    public Map<String, String> getColumnMapping()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<>(_sourceQueries.values());
        Map<String, Set<VisualizationSourceColumn>> allAliases = getColumnMapping(_columnFactory, queries);
        Map<String, String> colMap = new LinkedHashMap<>();

        // The default column mapping references the first available valid alias:
        for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : allAliases.entrySet())
        {
            colMap.put(entry.getKey(), entry.getValue().iterator().next().getAlias());

            if (entry.getValue().size() > 1)
            {
                for (VisualizationSourceColumn select : entry.getValue())
                {
                    VisualizationProvider provider = getVisualizationProvider(select.getSchemaName());
                    if (!provider.isJoinColumn(select, getViewContext().getContainer()))
                        colMap.put(select.getAlias(), select.getAlias());
                }
            }
        }

        return colMap;
    }

    private static void addToColMap(Map<String, Set<VisualizationSourceColumn>> colMap, String name, Set<VisualizationSourceColumn> newAliases)
    {
        Set<VisualizationSourceColumn> aliases = colMap.get(name);
        if (aliases == null)
        {
            aliases = new LinkedHashSet<>();
            colMap.put(name, aliases);
        }
        aliases.addAll(newAliases);
    }

    private static Map<String, Set<VisualizationSourceColumn>> getColumnMapping(VisualizationSourceColumn.Factory factory, Collection<IVisualizationSourceQuery> queries)
    {
        Map<String, Set<VisualizationSourceColumn>> colMap = new LinkedHashMap<>();

        // Add the sort columns first, since these are generally important to the user and should appear
        // on the left-hand side of any data grids.  (Subject ID is the most common sort column.)
        for (IVisualizationSourceQuery query : queries)
        {
            for (VisualizationSourceColumn sort : query.getSorts())
                addToColMap(colMap, sort.getOriginalName(), Collections.singleton(sort));
        }

        for (IVisualizationSourceQuery query : queries)
        {
            Map<String, Set<VisualizationSourceColumn>> queryColMap = query.getColumnNameToValueAliasMap(factory, false);
            for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : queryColMap.entrySet())
                addToColMap(colMap, entry.getKey(), entry.getValue());
        }
        return colMap;
    }

    public UserSchema getPrimarySchema()
    {
        if (_sourceQueries.isEmpty())
        {
            throw new NotFoundException("No primary schema found.");
        }
        VisualizationSourceQuery firstQuery = (VisualizationSourceQuery)pickFirst(_sourceQueries.values());
        return firstQuery.getSchema();
    }

    public String getFilterDescription()
    {
        StringBuilder builder = new StringBuilder();
        String sep = "";
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            for (SimpleFilter filter : query.getFilters())
            {
                if (filter != null)
                {
                    builder.append(sep).append(filter.getFilterText());
                    sep = " AND ";
                }
            }
        }
        return builder.toString();
    }

    static Object pickFirst(Collection set)
    {
        set.iterator().hasNext();
        return set.iterator().next();
    }

    public boolean isMetaDataOnly()
    {
        return _metaDataOnly;
    }

    public void setMetaDataOnly(boolean metaDataOnly)
    {
        _metaDataOnly = metaDataOnly;
    }

    public boolean isJoinToFirst()
    {
        return _joinToFirst;
    }

    public void setJoinToFirst(boolean joinToFirst)
    {
        _joinToFirst = joinToFirst;
    }

    private static String normalizeInterval(String interval)
    {
        // default to "Days"
        if (interval == null)
            interval = "Days";

        if (interval.endsWith("s"))
            interval = interval.substring(0, interval.length() - 1);
        return interval;
    }


    public static class GetDataTestCase extends Assert
    {
        final ViewContext context;


        static VisDataRequest.Measure m(String query, String name)
        {
            return new VisDataRequest.Measure().setSchemaName("vis_junit").setQueryName(query).setName(name);
        }
        static VisDataRequest.MeasureInfo mi(String query, String name, String time)
        {
            VisDataRequest.MeasureInfo mi = new VisDataRequest.MeasureInfo();
            mi.setMeasure(m(query, name)).setTime(time);
            return mi;
        }

        public GetDataTestCase()
        {
            ViewContext ctx = new ViewContext();
            ctx.setContainer(JunitUtil.getTestContainer());
            ctx.setUser( TestContext.get().getUser());
            this.context = ctx;
        }

        VisualizationSQLGenerator getVSQL(VisDataRequest query)
        {
            VisualizationSQLGenerator vs = new VisualizationSQLGenerator();
            vs.setViewContext(context);
            vs.fromVisDataRequest(query);
            return vs;
        }

        String getSQL(VisDataRequest q) throws SQLGenerationException, SQLException
        {
            VisualizationSQLGenerator gen = getVSQL(q);
            UserSchema schema = new VisTestSchema(context.getUser(), context.getContainer());
            return gen.getSQL();
        }

        Results getResults(VisDataRequest q) throws SQLGenerationException, SQLException
        {
            VisualizationSQLGenerator gen = getVSQL(q);
            UserSchema schema = new VisTestSchema(context.getUser(), context.getContainer());
            String sql = gen.getSQL();
            return QueryService.get().selectResults(schema, sql, null, null, true, true);
        }


        void dump(ResultSet rs) throws SQLException
        {
            rs.beforeFirst();
            ResultSetUtil.logData(rs);
        }


        @Test
        public void testOneTable() throws SQLGenerationException, SQLException
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo ptid = mi("demographics","participantid","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");

            VisDataRequest q = new VisDataRequest();
            q.addMeasure(age);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(48,r.getSize());
                assertEquals(3, r.getMetaData().getColumnCount());
                ColumnInfo ci = r.getColumnInfo(1);
                assertTrue(StringUtils.endsWith(ci.getName(), "_age"));
            }

            q.addMeasure(ptid);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(48,r.getSize());
                assertEquals(3, r.getMetaData().getColumnCount());
                ColumnInfo ci = r.getColumnInfo(2);
                assertTrue(StringUtils.endsWith(ci.getName(), "_participantid"));
            }

            q.addMeasure(gender);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(48,r.getSize());
                assertEquals(4, r.getMetaData().getColumnCount());
            }

            // select the same column twice
            // interestingly this ignores the duplicate column
            q.addMeasure(age);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(48,r.getSize());
                assertEquals(4, r.getMetaData().getColumnCount());
            }

            // this also ignores duplicates.  measure.alias only affects the json response
            VisDataRequest.MeasureInfo age1 = mi("demographics","age","visit");
            age1.getMeasure().setAlias("age1");
            VisDataRequest.MeasureInfo age2 = mi("demographics","age","visit");
            age2.getMeasure().setAlias("age2");
            q = new VisDataRequest();
            q.addMeasure(ptid).addMeasure(age1).addMeasure(age2);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(48,r.getSize());
                assertEquals(3, r.getMetaData().getColumnCount());
            }
        }


        @Test
        public void testOneTableWithValues() throws Exception
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");
            // NOTE: adding ptid to the select list will cause the sort/filter to be missed or ignored, tbat seems strange...
            VisDataRequest q = new VisDataRequest().addMeasure(age).addMeasure(gender);

            // it seems strange to filter by using a sort?  but OK
            VisDataRequest.Measure ptidList = new VisDataRequest.Measure("vis_junit", "demographics", "participantid");
            ptidList.setValues((List)VisTestSchema.humans);
            q.addSort(ptidList);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(VisTestSchema.humans.size(),r.getSize());
                assertEquals(4, r.getMetaData().getColumnCount());
            }
        }


        @Test
        public void testFilterQuery() throws Exception
        {

        }

        @Test
        public void testFilterURL() throws Exception
        {

        }

        @Test
        public void testOneTableWithGrouping() throws Exception
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");

            VisDataRequest q = new VisDataRequest();
            q.addMeasure(age);
            q.addGroupBy(gender.getMeasure());
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                // this is a surprise! we're implicitly grouping by participant,visit as well as gender
                assertTrue(48 == r.getSize());        // Female, female, Male, male, null
                assertEquals(8, r.getMetaData().getColumnCount());  ;  // gender, count(*), avg(), stddev(), stderr()
            }

            // Assay Flow
            q = new VisDataRequest()
                    .addMeasure(mi("flow", "cellcount", "visit"))
                    .addMeasure(mi("flow", "participantid", "visit"))
                    .addMeasure(mi("flow", "sequencenum", "visit"))
                    .addMeasure(mi("flow", "antigen", "visit"))
                    .addMeasure(mi("flow", "population", "visit"));
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(192, r.getSize());     // 16*3*2*2
                assertEquals(5, r.getMetaData().getColumnCount());
            }

            /*  TODO
            This so doesn't work how you expect, this causes a self join on a subset of keys
            and causes row duplication then grouping on that, and the wrong results and the wrong number of rows...
            q = new VisDataRequest()
                    .addMeasure(mi("flow", "cellcount", "sequencenum"))
                    .addGroupBy(m("flow", "population"));
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(192, r.getSize());     // 16*3*2*2
                assertEquals(8, r.getMetaData().getColumnCount());
            }
            */

            // it seems that perhaps you can only really group on "demographic" properties
            // and expect to get a meaningful answer
            q = new VisDataRequest()
                    .addMeasure(mi("flow","cellcount","visit"))
                    .addGroupBy(m("demographics", "gender"));
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(3, r.getSize());     // Male, Female, -
                assertEquals(5, r.getMetaData().getColumnCount());
            }
        }


        @Test
        public void testTwoTablesDemStudy() throws Exception
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo ptid = mi("demographics","participantid","visit");
            VisDataRequest.MeasureInfo study = mi("demographics","study","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");
            VisDataRequest.MeasureInfo condition = mi("study","condition","visit");      // shouldn't need to specify time=visit

            // SELECT demographics.ptid, study.condition
            VisDataRequest q = new VisDataRequest();
            q.addMeasure(ptid).addMeasure(condition);
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertTrue(48 == r.getSize());
                assertEquals(4, r.getMetaData().getColumnCount());
            }

            // SELECT study.condition, AVG(age)
            q = new VisDataRequest();
            q.addMeasure(age);
            q.addGroupBy(condition.getMeasure());
            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(3, r.getSize());
                assertEquals(5, r.getMetaData().getColumnCount());  ;  // gender, count(*), avg(), stddev(), stderr()
            }
        }


        @Test
        public void testPivot()
        {

        }

        @Test
        public void testGroupBy()
        {

        }

        @Test
        public void testFullOuter()
        {

        }
    }

}
