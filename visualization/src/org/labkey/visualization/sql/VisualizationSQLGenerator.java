/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.IVisualizationSourceQuery;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisualizationAggregateColumn;
import org.labkey.api.visualization.VisualizationIntervalColumn;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
 * User: brittp
 * Date: Jan 25, 2011 4:27:46 PM
 */
public class VisualizationSQLGenerator implements CustomApiForm, HasViewContext
{

    private Map<String, VisualizationSourceQuery> _sourceQueries = new LinkedHashMap<>();
    private Map<String, VisualizationIntervalColumn> _intervals = new HashMap<>();
    private List<VisualizationSourceColumn> _groupBys = new ArrayList<>();

    private ViewContext _viewContext;
    private VisualizationSourceColumn.Factory _columnFactory = new VisualizationSourceColumn.Factory();
    private boolean _metaDataOnly;
    private Integer _limit;

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

    @Override
    public void bindProperties(Map<String, Object> properties)
    {
        try
        {
            _bindProperties(properties);
        }
        catch (IllegalArgumentException x)
        {
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw x;
        }
    }

    private void _bindProperties(Map<String, Object> properties)
    {
        Object measuresProp = properties.get("measures");
        if (measuresProp != null)
        {
            VisualizationSourceQuery previous = null;
            for (Map<String, Object> measureInfo : ((JSONArray) measuresProp).toJSONObjectArray())
            {
                Map<String, Object> measureProperties = (Map<String, Object>) measureInfo.get("measure");
                if (null == measureProperties || measureProperties.isEmpty())
                {
                    throw new IllegalArgumentException("The 'measure' property is required for each of the elements in the measures array.");
                }

                Map<String, Object> dimensionProperties = (Map<String, Object>) measureInfo.get("dimension");

                VisualizationSourceQuery query;
                VisualizationSourceColumn measureCol;
                if (dimensionProperties != null && !dimensionProperties.isEmpty())
                {
                    // this column is the value column of a pivot, so we assume that it's an aggregate
                    measureCol = new VisualizationAggregateColumn(getViewContext(), measureProperties);
                    query = ensureSourceQuery(_viewContext.getContainer(), measureCol, previous);
                    query.addAggregate((VisualizationAggregateColumn) measureCol);
                    VisualizationSourceColumn pivot = _columnFactory.create(getViewContext(), dimensionProperties);
                    query.setPivot(pivot);
                }
                else
                {
                    measureCol = _columnFactory.create(getViewContext(), measureProperties);
                    query = ensureSourceQuery(_viewContext.getContainer(), measureCol, previous);
                    query.addSelect(measureCol, true);

                    if (measureProperties.containsKey("isDemographic"))
                        query.setSkipVisitJoin((Boolean) measureProperties.get("isDemographic"));
                }

                Object timeAxis = measureInfo.get("time");
                VisualizationProvider.ChartType type;
                if (timeAxis instanceof String)
                {
                    if ("date".equalsIgnoreCase((String) timeAxis))
                        type = VisualizationProvider.ChartType.TIME_DATEBASED;
                    else if ("visit".equalsIgnoreCase((String) timeAxis))
                        type = VisualizationProvider.ChartType.TIME_VISITBASED;
                    else
                        throw new IllegalArgumentException("Unknown time value: " + timeAxis);
                }
                else
                    throw new IllegalArgumentException("Only time charts are currently supported: expected 'time' property on each measure.");

                VisualizationProvider provider = ensureVisualizationProvider(query.getSchemaName(), type);
                switch (type)
                {
                    case TIME_DATEBASED:
                        Map<String, Object> dateOptions = (Map<String, Object>) measureInfo.get("dateOptions");

                        if (dateOptions != null && !dateOptions.isEmpty())
                        {
                            String interval = (String) dateOptions.get("interval");
                            interval = normalizeInterval(interval);
                            VisualizationIntervalColumn newInterval;

                            Map<String, Object> dateProperties = (Map<String, Object>) dateOptions.get("dateCol");
                            Map<String, Object> zeroDateProperties = (Map<String, Object>) dateOptions.get("zeroDateCol");

                            if (dateProperties != null && !dateProperties.isEmpty() && zeroDateProperties != null && !zeroDateProperties.isEmpty())
                            {
                                VisualizationSourceColumn dateCol = _columnFactory.create(getViewContext(), dateProperties);
                                dateCol.setAllowNullResults(measureCol.isAllowNullResults());
                                ensureSourceQuery(_viewContext.getContainer(), dateCol, query).addSelect(dateCol, false);

                                VisualizationSourceColumn zeroDateCol = _columnFactory.create(getViewContext(), zeroDateProperties);
                                zeroDateCol.setAllowNullResults(false);
                                ensureSourceQuery(_viewContext.getContainer(), zeroDateCol, query).addSelect(zeroDateCol, false);

                                newInterval = new VisualizationIntervalColumn(zeroDateCol, dateCol, interval, false);
                            }
                            else if (dateOptions.containsKey("zeroDayVisitTag"))
                            {
                                VisualizationSourceColumn zeroDayCol = null;
                                boolean useProtocolDay = (null == dateOptions.get("useProtocolDay") || (boolean)dateOptions.get("useProtocolDay"));

                                //  Issue 20459: handle 'Unaligned' (i.e. null zero day) case for calculating weeks/months
                                if (null != dateOptions.get("zeroDayVisitTag"))
                                {
                                    String zeroDayVisitTag = (String)dateOptions.get("zeroDayVisitTag");
                                    zeroDayCol = _columnFactory.create(getPrimarySchema(), "VisualizationVisitTag", "ZeroDay", true,
                                            zeroDayVisitTag, useProtocolDay, interval);
                                    zeroDayCol.setAllowNullResults(false);
                                    ensureSourceQuery(_viewContext.getContainer(), zeroDayCol, query).addSelect(zeroDayCol, false);
                                }

                                newInterval = new VisualizationIntervalColumn(zeroDayCol, measureCol, interval, true);
                            }
                            else
                            {
                                throw new IllegalArgumentException("The 'zeroDayVisitTag' property or the 'dateCol' and 'zeroDateCol' properties are requried.");
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

                provider.addExtraSelectColumns(_columnFactory, query);
                previous = query;
            }
        }

        Object sortsProp = properties.get("sorts");
        if (sortsProp != null)
        {
            for (Map<String, Object> sortInfo : ((JSONArray) sortsProp).toJSONObjectArray())
            {
                VisualizationSourceColumn sort = _columnFactory.create(getViewContext(), sortInfo);
                getSourceQuery(sort, true).addSort(sort);
            }
        }

        Object filterUrlString = properties.get("filterUrl");
        if (filterUrlString != null)
        {
            ActionURL filterUrl = new ActionURL((String) filterUrlString);
            String queryName = (String) properties.get("filterQuery");
            VisualizationSourceQuery query = _sourceQueries.get(queryName);
            if (query != null)
            {
                SimpleFilter filter = new SimpleFilter(filterUrl, VisualizationController.FILTER_DATAREGION);
                query.setFilter(filter);
            }
        }

        Object groupBys = properties.get("groupBys");
        if (groupBys != null)
        {
            for (Map<String, Object> additionalSelectInfo : ((JSONArray)groupBys).toJSONObjectArray())
            {
                _groupBys.add(_columnFactory.create(getViewContext(), additionalSelectInfo));
            }
        }

        ensureJoinColumns();
        
        _metaDataOnly = BooleanUtils.toBooleanDefaultIfNull((Boolean)properties.get("metaDataOnly"), false);

        Object limit = properties.get("limit");
        if (limit != null)
        {
            if (NumberUtils.isDigits(limit.toString()))
                _limit = Integer.parseInt(limit.toString());
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

        String joinOperator = "INNER JOIN";
        for (VisualizationSourceQuery innerJoinQuery : innerJoinQueries)
            joinOperator = getVisualizationProvider(innerJoinQuery.getSchemaName()).getJoinOperator(getViewContext().getContainer());

        String sql = getSQL(null, _columnFactory, queries, new ArrayList<>(_intervals.values()), joinOperator, _groupBys.isEmpty(), _limit != null);

        if (!_groupBys.isEmpty())
        {
            IVisualizationSourceQuery joinQuery = innerJoinQueries.isEmpty() ? outerJoinQueries.iterator().next() : innerJoinQueries.iterator().next();
            sql = wrapInGroupBy(joinQuery, queries, sql);
        }

        if (_limit != null)
        {
            StringBuilder sb = new StringBuilder(sql);

            sb.append(" LIMIT ").append(_limit);
            sql = sb.toString();
        }
        return sql;
    }

    private String wrapInGroupBy(IVisualizationSourceQuery joinQuery, List<IVisualizationSourceQuery> queries, String sql)
            throws SQLGenerationException
    {
        Map<String, Set<VisualizationSourceColumn>> columnAliases = getColumnMapping(_columnFactory, queries);

        StringBuilder aggregatedSQL = new StringBuilder("SELECT ");
        String separator = "";
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
                    aggregatedSQL.append(", AVG(x.\"" + alias + "\") AS \"" + alias + "\"");
                    aggregatedSQL.append(", STDDEV(x.\"" + alias + "\") AS \"" + alias + "_STDDEV\"");
                    aggregatedSQL.append(", STDERR(x.\"" + alias + "\") AS \"" + alias + "_STDERR\"");
                    aggregatedSQL.append("\n");
                }
            }
        }

        aggregatedSQL.append("\n FROM (");
        aggregatedSQL.append(sql);
        aggregatedSQL.append(") x");

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
                aggregatedSQL.append(columnAliases.get(pair.getKey().getOriginalName()).iterator().next().getSQLAlias());
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

    private static IVisualizationSourceQuery findQuery(VisualizationSourceColumn column, Collection<IVisualizationSourceQuery> queries)
    {
        for (IVisualizationSourceQuery query : queries)
        {
            if (query.contains(column))
                return query;
        }
        return null;
    }

    public String getSQL(IVisualizationSourceQuery parentQuery, VisualizationSourceColumn.Factory factory,
                                Collection<IVisualizationSourceQuery> queries, List<VisualizationIntervalColumn> intervals,
                                String joinOperator, boolean includeOrderBys, boolean hasRowLimit) throws SQLGenerationException
    {
        // Reorder the queries in case one can join to the other, but not the reverse. For example,
        // we can join from a standard particiapnt visit/date dataset to a demographic dataset, but not the reverse.
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
            String selectAlias;
            if (parentQuery != null)
                selectAlias = parentQuery.getSelectListName(selectAliases);
            else
            {
                VisualizationSourceColumn col = selectAliases.iterator().next();
                String label = col.getLabel();
                selectAlias = col.getSQLAlias() + (null==label ? " @preservetitle" : " @title='" + StringUtils.replace(label,"'","''") + "'");
            }
            masterSelectList.append(sep).append(selectAlias);
            sep = ",\n\t";
        }

        for (int i = 0, intervalsSize = intervals.size(); i < intervalsSize; i++)
        {
            VisualizationIntervalColumn interval = intervals.get(i);
            // if the end date has multiple aliases, set it to be the first
            Set<VisualizationSourceColumn> intervalAliases = allAliases.get(interval.getEndCol().getOriginalName());
            if (intervalAliases != null && intervalAliases.size() > 1)
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
                sql.append("SELECT ").append(masterSelectList).append(" FROM\n");
            else
                sql.append("\n").append(joinOperator).append("\n");
            String querySql = query.getSQL(factory);
            sql.append("(").append(querySql).append(") AS ").append(query.getSQLAlias()).append("\n");
            if (query.getJoinTarget() != null)
            {
                sql.append("ON ");
                String andSep = "";
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> condition : query.getJoinConditions())
                {
                    // either left or right should be our current query
                    VisualizationSourceColumn leftColumn = condition.getKey();
                    IVisualizationSourceQuery leftQuery = findQuery(condition.getKey(), queries);
                    VisualizationSourceColumn rightColumn = condition.getValue();
                    IVisualizationSourceQuery rightQuery = findQuery(condition.getValue(), queries);

                    // issue 20526: inner join gets confused about which alias to use for the join condition after coalesce
                    String rightAlias = parentQuery != null ? rightColumn.getSQLAlias() : rightColumn.getSQLOther();

                    sql.append(andSep);
                    andSep = " AND ";
                    sql.append(leftQuery.getSQLAlias()).append(".").append(leftColumn.getSQLAlias()).append(" = ");
                    sql.append(rightQuery.getSQLAlias()).append(".").append(rightAlias).append("\n");
                }
            }
        }

        if (includeOrderBys && !orderBys.isEmpty())
        {
            sql.append(getOrderByClause(orderBys, hasRowLimit));
        }

        return sql.toString();
    }

    private static String getOrderByClause(Map<VisualizationSourceColumn, IVisualizationSourceQuery> orderBys, boolean hasRowLimit)
    {
        if (orderBys.isEmpty())
        {
            return "";
        }
        String sep = "";
        StringBuilder sql = new StringBuilder("ORDER BY ");
        for (Map.Entry<VisualizationSourceColumn, IVisualizationSourceQuery> orderBy : orderBys.entrySet())
        {
            sql.append(sep).append(orderBy.getValue().getSQLAlias()).append(".").append(orderBy.getKey().getSQLAlias());
            sep = ", ";
        }
        if (findSchema(orderBys.keySet()).getDbSchema().getSqlDialect().isSqlServer() && !hasRowLimit)
            sql.append(" LIMIT 1000000");
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

    public List<Map<String, String>> getColumnAliases()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<IVisualizationSourceQuery>(_sourceQueries.values());
        Map<String, Set<VisualizationSourceColumn>> allAliases = getColumnMapping(_columnFactory, queries);
        Set<Map<String, String>> result = new LinkedHashSet<>();
        // The default column mapping references the first available valid alias:
        for (Map.Entry<String, Set<VisualizationSourceColumn>> entry : allAliases.entrySet())
        {
            result.add(entry.getValue().iterator().next().toJSON(entry.getKey()));

            if (entry.getValue().size() > 1)
            {
                for (VisualizationSourceColumn select : entry.getValue())
                {
                    VisualizationProvider provider = getVisualizationProvider(select.getSchemaName());
                    if (!provider.isJoinColumn(select, getViewContext().getContainer()))
                        result.add(select.toJSON(select.getAlias()));
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Deprecated
    /**
     * This method and its usage should go away as soon as the client has been migrated to use the columnAliases JSON
     * values, since they contain everything this mapping does and more.
     */
    public Map<String, String> getColumnMapping()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<IVisualizationSourceQuery>(_sourceQueries.values());
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

        /*
        // Now that we have the full set of columns, we can take a pass through to eliminate the columns on the right
        // side of join clauses, since we know the columns contain duplicate data. We leave a key in the column map
        // for the originally requested column name, but replace the value column, so the requestor can use whichever
        // column name they like to find the results.
        Map<String, String> selectAliasRemapping = new HashMap<String, String>();
        for (IVisualizationSourceQuery query : queries)
        {
            if (query.getJoinConditions() != null)
            {
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : query.getJoinConditions())
                    selectAliasRemapping.put(join.getKey().getAlias(), join.getValue().getAlias());
            }
        }

        for (Map.Entry<String, String> mapping : colMap.entrySet())
        {
            String originalAlias = mapping.getValue();
            String remappedAlias = selectAliasRemapping.get(originalAlias);
            if (remappedAlias != null)
                mapping.setValue(remappedAlias);
        }
        */

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
        VisualizationSourceQuery firstQuery = _sourceQueries.values().iterator().next();
        return firstQuery.getSchema();
    }

    public String getFilterDescription()
    {
        StringBuilder builder = new StringBuilder();
        String sep = "";
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            if (query.getFilter() != null)
            {
                builder.append(sep).append(query.getFilter().getFilterText());
                sep = " AND ";
            }
        }
        return builder.toString();
    }

    public boolean isMetaDataOnly()
    {
        return _metaDataOnly;
    }

    public void setMetaDataOnly(boolean metaDataOnly)
    {
        _metaDataOnly = metaDataOnly;
    }

    private static String normalizeInterval(String interval)
    {
        if (interval.endsWith("s"))
            interval = interval.substring(0, interval.length() - 1);
        return interval;
    }
}
