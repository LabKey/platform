package org.labkey.visualization.sql;

import org.json.JSONArray;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
 * Copyright (c) 2011 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Jan 25, 2011 4:27:46 PM
 */
public class VisualizationSQLGenerator implements CustomApiForm, HasViewContext
{
    public static class GenerationException extends Exception
    {
        public GenerationException(String message)
        {
            super(message);
        }

        public GenerationException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    private Map<String, VisualizationSourceQuery> _sourceQueries = new LinkedHashMap<String, VisualizationSourceQuery>();
    private Map<String, VisualizationIntervalColumn> _intervals = new HashMap<String, VisualizationIntervalColumn>();
    private ViewContext _viewContext;
    private VisualizationSourceColumn.Factory _columnFactory = new VisualizationSourceColumn.Factory();

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

    public static enum ChartType
    {
        TIME_DATEBASED,
        TIME_VISITBASED
    }


    private Map<String, VisualizationProvider> _providers = new CaseInsensitiveHashMap<VisualizationProvider>();

    public VisualizationProvider ensureVisualizationProvider(String schema, ChartType type)
    {
        VisualizationProvider provider = _providers.get(schema);
        if (provider == null)
        {
            provider = VisualizationController.createVisualizationProvider(schema);
            provider.configure(type);
            _providers.put(schema, provider);
        }
        else
        {
            if (type != provider.getType())
                throw new IllegalArgumentException("Cannot generate SQL for two chart types with a single call.  Chart type was " + provider.getType() + ", but is now requested as " + type);
        }
        return provider;
    }

    public VisualizationProvider getVisualizationProvider(String schema)
    {
        VisualizationProvider provider = _providers.get(schema);
        if (provider == null)
            throw new IllegalStateException("No provider configured for schema " + schema);
        return provider;
    }

    @Override
    public void bindProperties(Map<String, Object> properties)
    {
        Object measuresProp = properties.get("measures");
        if (measuresProp != null)
        {
            VisualizationSourceQuery previous = null;
            for (Map<String, Object> measureInfo : ((JSONArray) measuresProp).toJSONObjectArray())
            {
                Map<String, Object> measureProperties = (Map<String, Object>) measureInfo.get("measure");
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
                    query.addSelect(measureCol);
                }

                Object timeAxis = measureInfo.get("time");
                ChartType type = null;
                if (timeAxis instanceof String)
                {
                    if ("date".equalsIgnoreCase((String) timeAxis))
                        type = ChartType.TIME_DATEBASED;
                    else if ("visit".equalsIgnoreCase((String) timeAxis))
                        type = ChartType.TIME_VISITBASED;
                    else
                        throw new IllegalArgumentException("Unknown time value: " + timeAxis);
                }
                else
                    throw new IllegalStateException("Only time charts are currently supported: expected 'time' property on each measure.");

                switch (type)
                {
                    case TIME_DATEBASED:
                        Map<String, Object> dateOptions = (Map<String, Object>) measureInfo.get("dateOptions");
                        Map<String, Object> dateProperties = (Map<String, Object>) dateOptions.get("dateCol");
                        Map<String, Object> zeroDateProperties = (Map<String, Object>) dateOptions.get("zeroDateCol");
                        if (zeroDateProperties != null && !zeroDateProperties.isEmpty())
                        {
                            VisualizationSourceColumn dateCol = _columnFactory.create(getViewContext(), dateProperties);
                            dateCol.setAllowNullResults(measureCol.isAllowNullResults());
                            ensureSourceQuery(_viewContext.getContainer(), dateCol, query).addSelect(dateCol);
                            VisualizationSourceColumn zeroDateCol = _columnFactory.create(getViewContext(), zeroDateProperties);
                            zeroDateCol.setAllowNullResults(false);
                            ensureSourceQuery(_viewContext.getContainer(), zeroDateCol, query).addSelect(zeroDateCol);
                            String interval = (String) dateOptions.get("interval");
                            if (interval != null)
                            {
                                VisualizationIntervalColumn newInterval = new VisualizationIntervalColumn(zeroDateCol, dateCol, interval);
                                if (!_intervals.containsKey(newInterval.getFullAlias()))
                                    _intervals.put(newInterval.getFullAlias(), newInterval);
                            }
                        }
                        break;
                    case TIME_VISITBASED:
                        // No special handling needed for visit-based charts
                        break;
                }

                VisualizationProvider provider = ensureVisualizationProvider(query.getSchemaName(), type);
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

        ensureJoinColumns();
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
                VisualizationProvider provider = getVisualizationProvider(query.getSchemaName());
                if (provider == null)
                    throw new IllegalArgumentException("No visualization provider registered for schema \"" + query.getSchemaName() + "\".");
                List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions = new ArrayList<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>();
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : provider.getJoinColumns(_columnFactory, query, joinTarget))
                {
                    // Make sure we're selecting all the columns we need to join on:
                    VisualizationSourceColumn left = join.getKey();
                    getSourceQuery(join.getKey(), true).addSelect(left);
                    VisualizationSourceColumn right = join.getValue();
                    getSourceQuery(join.getValue(), true).addSelect(right);
                    // We need to filter both left and right queries by the same values (for the same columns), since we
                    // may be doing an outer join.
                    left.syncValues(right);
                    joinConditions.add(new Pair<VisualizationSourceColumn, VisualizationSourceColumn>(left, right));
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
            query = new VisualizationSourceQuery(container, column.getSchema(), column.getQueryName(), joinQuery);
            _sourceQueries.put(queryName, query);
        }
        return query;
    }

    public VisualizationSQLGenerator()
    {
        super();    //To change body of overridden methods use File | Settings | File Templates.
    }

    public String getSQL() throws VisualizationSQLGenerator.GenerationException
    {
        return getSQL(null);
    }

    public String getSQL(IVisualizationSourceQuery parentQuery) throws VisualizationSQLGenerator.GenerationException
    {
        Set<IVisualizationSourceQuery> outerJoinQueries = new LinkedHashSet<IVisualizationSourceQuery>();
        Set<VisualizationSourceQuery> innerJoinQueries = new LinkedHashSet<VisualizationSourceQuery>();
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            if (query.requireInnerJoin())
                innerJoinQueries.add(query);
            else
                outerJoinQueries.add(query);
        }

        // Create a single, nested outer join query to gather up all the outer-join results
        List<IVisualizationSourceQuery> queries = new ArrayList<IVisualizationSourceQuery>();
        IVisualizationSourceQuery nestedOuterJoinQuery = null;
        if (outerJoinQueries.size() > 1)
        {
            nestedOuterJoinQuery = new OuterJoinSourceQuery(outerJoinQueries);
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
        return getSQL(parentQuery, _columnFactory, queries, new ArrayList<VisualizationIntervalColumn>(_intervals.values()), "INNER JOIN", true);
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

    public static String getSQL(IVisualizationSourceQuery parentQuery, VisualizationSourceColumn.Factory factory, Collection<IVisualizationSourceQuery> queries, List<VisualizationIntervalColumn> intervals, String joinOperator, boolean includeOrderBys) throws VisualizationSQLGenerator.GenerationException
    {
        // Now that we have the full list of columns we want to select, we can generate our select list
        Map<String, Set<String>> allAliases = getColumnMapping(factory, queries);
        StringBuilder masterSelectList = new StringBuilder();
        String sep = "";
        for (Set<String> selectAliases : allAliases.values())
        {
            String selectAlias;
            if (parentQuery != null)
                selectAlias = parentQuery.getSelectListName(selectAliases);
            else
                selectAlias = "\"" + selectAliases.iterator().next() + "\"";
            masterSelectList.append(sep).append(selectAlias);
            sep = ",\n\t";
        }

        for (int i = 0, intervalsSize = intervals.size(); i < intervalsSize; i++)
        {
            VisualizationIntervalColumn interval = intervals.get(i);
            // if the end date has multiple aliases, set it to be the first
            Set<String> intervalAliases = allAliases.get(interval.getEndDate().getOriginalName());
            if (intervalAliases.size() > 1)
            {
                interval.getEndDate().setOtherAlias(factory.get(intervalAliases.iterator().next()).getAlias());
            }
            String alias = (intervalsSize > 1) ? interval.getFullAlias() : interval.getSimpleAlias();
            masterSelectList.append(sep).append(interval.getSQL()).append(" AS ").append(alias);
        }

        Map<VisualizationSourceColumn, IVisualizationSourceQuery> orderBys = new LinkedHashMap<VisualizationSourceColumn, IVisualizationSourceQuery>();
        StringBuilder sql = new StringBuilder();
        for (IVisualizationSourceQuery query : queries)
        {
            for (VisualizationSourceColumn orderBy : query.getSorts())
            {
                Set<String> orderByAliases = allAliases.get(orderBy.getOriginalName());
                if (orderByAliases.size() > 1)
                    orderBys.put(factory.get(orderByAliases.iterator().next()), query);
                else
                    orderBys.put(orderBy, query);
            }
            if (sql.length() == 0)
                sql.append("SELECT ").append(masterSelectList).append(" FROM\n");
            else
                sql.append("\n").append(joinOperator).append("\n");
            String querySql = query.getSQL(factory);
            sql.append("(").append(querySql).append(") AS ").append(query.getAlias()).append("\n");
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

                    sql.append(andSep);
                    andSep = " AND ";
                    sql.append(leftQuery.getAlias()).append(".").append(leftColumn.getAlias()).append(" = ");
                    sql.append(rightQuery.getAlias()).append(".").append(rightColumn.getAlias()).append("\n");
                }
            }
        }

        if (includeOrderBys && !orderBys.isEmpty())
        {
            sep = "";
            sql.append("ORDER BY ");
            UserSchema firstSchema = null;
            for (Map.Entry<VisualizationSourceColumn, IVisualizationSourceQuery> orderBy : orderBys.entrySet())
            {
                if (firstSchema == null)
                    firstSchema = orderBy.getKey().getSchema();
                sql.append(sep).append(orderBy.getValue().getAlias()).append(".").append(orderBy.getKey().getAlias());
                sep = ", ";
            }
            assert firstSchema != null : "Should have at least one column with a schema.";
            DefaultSchema defSchema = DefaultSchema.get(firstSchema.getUser(), firstSchema.getContainer());
            if (defSchema.getDbSchema().getSqlDialect().isSqlServer())
                sql.append(" LIMIT 1000000");
        }

        return sql.toString();
    }

    public Map<String, String> getColumnMapping()
    {
        Collection<IVisualizationSourceQuery> queries = new LinkedHashSet<IVisualizationSourceQuery>(_sourceQueries.values());
        Map<String, Set<String>> allAliases = getColumnMapping(_columnFactory, queries);
        Map<String, String> colMap = new LinkedHashMap<String, String>();
        // The default column mapping references the first available valid alias:
        for (Map.Entry<String, Set<String>> entry : allAliases.entrySet())
            colMap.put(entry.getKey(), entry.getValue().iterator().next());

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

        return colMap;
    }

    private static void addToColMap(Map<String, Set<String>> colMap, String name, Set<String> newAliases)
    {
        Set<String> aliases = colMap.get(name);
        if (aliases == null)
        {
            aliases = new LinkedHashSet<String>();
            colMap.put(name, aliases);
        }
        aliases.addAll(newAliases);
    }

    private static Map<String, Set<String>> getColumnMapping(VisualizationSourceColumn.Factory factory, Collection<IVisualizationSourceQuery> queries)
    {
        Map<String, Set<String>> colMap = new LinkedHashMap<String, Set<String>>();

        // Add the sort columns first, since these are generally important to the user and should appear
        // on the left-hand side of any data grids.  (Subject ID is the most common sort column.)
        for (IVisualizationSourceQuery query : queries)
        {
            for (VisualizationSourceColumn sort : query.getSorts())
                addToColMap(colMap, sort.getOriginalName(), Collections.singleton(sort.getAlias()));
        }

        for (IVisualizationSourceQuery query : queries)
        {
            Map<String, Set<String>> queryColMap = query.getColumnNameToValueAliasMap(factory);
            for (Map.Entry<String, Set<String>> entry : queryColMap.entrySet())
                addToColMap(colMap, entry.getKey(), entry.getValue());
        }
        return colMap;
    }

    public UserSchema getPrimarySchema()
    {
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
}
