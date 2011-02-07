package org.labkey.visualization.sql;

import org.json.JSONArray;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
 * Copyright (c) 2008-2010 LabKey Corporation
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
    private Map<String, VisualizationSourceQuery> _sourceQueries = new LinkedHashMap<String, VisualizationSourceQuery>();
    private List<VisualizationIntervalColumn> _intervals = new ArrayList<VisualizationIntervalColumn>();
    private ViewContext _viewContext;

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

    @Override
    public void bindProperties(Map<String, Object> properties)
    {
        Object measuresProp = properties.get("measures");
        if (measuresProp != null)
        {
            VisualizationSourceQuery previous = null;
            for (Map<String, Object> measureInfo : ((JSONArray) measuresProp).toJSONObjectArray())
            {
                Map<String, Object> axisInfo = (Map<String, Object>) measureInfo.get("axis");
                Map<String, Object> measureProperties = (Map<String, Object>) measureInfo.get("measure");
                Map<String, Object> dimensionProperties = (Map<String, Object>) measureInfo.get("dimension");

                VisualizationSourceQuery query;
                VisualizationSourceColumn col;
                if (dimensionProperties != null && !dimensionProperties.isEmpty())
                {
                    // this column is the value column of a pivot, so we assume that it's an aggregate
                    col = new VisualizationAggregateColumn(getViewContext(), measureProperties);
                    query = ensureSourceQuery(_viewContext.getContainer(), col, previous);
                    query.addAggregate((VisualizationAggregateColumn) col);
                    VisualizationSourceColumn pivot = new VisualizationSourceColumn(getViewContext(), dimensionProperties);
                    query.setPivot(pivot);
                }
                else
                {
                    col = new VisualizationSourceColumn(getViewContext(), measureProperties);
                    query = ensureSourceQuery(_viewContext.getContainer(), col, previous);
                    query.addSelect(col);
                }

                Object timeAxis = axisInfo.get("timeAxis");
                if (timeAxis instanceof String && Boolean.parseBoolean((String) timeAxis))
                {
                    Map<String, Object> dateOptions = (Map<String, Object>) measureInfo.get("dateOptions");
                    Map<String, Object> zeroDateProperties = (Map<String, Object>) dateOptions.get("zeroDateCol");
                    if (zeroDateProperties != null)
                    {
                        VisualizationSourceColumn zeroDateMeasure = new VisualizationSourceColumn(getViewContext(), zeroDateProperties);
                        ensureSourceQuery(_viewContext.getContainer(), zeroDateMeasure, query).addSelect(zeroDateMeasure);
                        String interval = (String) dateOptions.get("interval");
                        if (interval != null)
                            _intervals.add(new VisualizationIntervalColumn(zeroDateMeasure, col, interval));
                    }
                }
                previous = query;
            }
        }

        Object sortsProp = properties.get("sorts");
        if (sortsProp != null)
        {
            for (Map<String, Object> sortInfo : ((JSONArray) sortsProp).toJSONObjectArray())
            {
                VisualizationSourceColumn sort = new VisualizationSourceColumn(getViewContext(), sortInfo);
                getSourceQuery(sort, true).addSort(sort);
            }
        }

        ensureJoinColumns();
    }

    private void ensureJoinColumns()
    {
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            VisualizationSourceQuery joinTarget = query.getJoinTarget();
            if (joinTarget != null)
            {
                if (!joinTarget.getSchemaName().equalsIgnoreCase(query.getSchemaName()))
                {
                    throw new IllegalArgumentException("Cross-schema joins are not yet supported.  Attempt to join " +
                            query.getDisplayName() + " to " + joinTarget.getDisplayName());
                }
                VisualizationProvider provider = VisualizationController.getVisualizationProviders().get(query.getSchemaName());
                if (provider == null)
                    throw new IllegalArgumentException("No visualization provider registered for schema \"" + query.getSchemaName() + "\".");
                List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions = provider.getJoinColumns(query, joinTarget);
                query.setJoinConditions(joinConditions);
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> join : joinConditions)
                {
                    // Make sure we're selecting all the columns we need to join on:
                    getSourceQuery(join.getKey(), true).addSelect(join.getKey());
                    getSourceQuery(join.getValue(), true).addSelect(join.getValue());
                }
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

    public String getSQL()
    {
        // Now that we have the full list of columns we want to select, we can
        StringBuilder masterSelectList = new StringBuilder();
        String sep = "";
        for (String selectAlias : getColumnMapping().values())
        {
            masterSelectList.append(sep).append("\"").append(selectAlias).append("\"");
            sep = ",\n\t";
        }

        for (int i = 0, intervalsSize = _intervals.size(); i < intervalsSize; i++)
        {
            VisualizationIntervalColumn interval = _intervals.get(i);
            String alias = (intervalsSize > 1) ? interval.getFullAlias() : interval.getSimpleAlias();
            masterSelectList.append(sep).append(interval.getSQL()).append(" AS ").append(alias);
        }

        Map<VisualizationSourceColumn, VisualizationSourceQuery> orderBys = new LinkedHashMap<VisualizationSourceColumn, VisualizationSourceQuery>();
        StringBuilder sql = new StringBuilder();
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            for (VisualizationSourceColumn orderBy : query.getSorts())
                orderBys.put(orderBy, query);
            if (sql.length() == 0)
                sql.append("SELECT ").append(masterSelectList).append(" FROM\n");
            else
                sql.append("\nINNER JOIN\n");
            String querySql = query.getSQL();
            sql.append("(").append(querySql).append(") AS ").append(query.getAlias()).append("\n");
            if (query.getJoinTarget() != null)
            {
                sql.append("ON ");
                for (Pair<VisualizationSourceColumn, VisualizationSourceColumn> condition : query.getJoinConditions())
                {
                    // either left or right should be our current query
                    VisualizationSourceColumn leftColumn = condition.getKey();
                    VisualizationSourceQuery leftQuery = getSourceQuery(condition.getKey(), true);
                    VisualizationSourceColumn rightColumn = condition.getValue();
                    VisualizationSourceQuery rightQuery = getSourceQuery(condition.getValue(), true);

                    sql.append(leftQuery.getAlias()).append(".").append(leftColumn.getAlias()).append(" = ");
                    sql.append(rightQuery.getAlias()).append(".").append(rightColumn.getAlias()).append("\n");
                }
            }
        }

        if (!orderBys.isEmpty())
        {
            sep = "";
            sql.append("ORDER BY ");
            for (Map.Entry<VisualizationSourceColumn, VisualizationSourceQuery> orderBy : orderBys.entrySet())
            {
                sql.append(sep).append(orderBy.getValue().getAlias()).append(".").append(orderBy.getKey().getAlias());
                sep = ", ";
            }
        }

        return sql.toString();
    }

    public Map<String, String> getColumnMapping()
    {
        Map<String, String> colMap = new LinkedHashMap<String, String>();
        for (VisualizationSourceQuery query : _sourceQueries.values())
        {
            Set<VisualizationAggregateColumn> aggregates = query.getAggregates();
            if (!aggregates.isEmpty())
            {
                for (VisualizationAggregateColumn aggregate : aggregates)
                {
                    if (query.getPivot() != null && !query.getPivot().getValues().isEmpty())
                    {
                        // Aggregate with pivot:
                        for (Object pivotValue : query.getPivot().getValues())
                        {
                            colMap.put(pivotValue.toString(), pivotValue.toString() + "::" + aggregate.getAlias());
                        }
                    }
                    else
                    {
                        // Aggregate without pivot (simple grouping)
                        colMap.put(aggregate.getOriginalName(), aggregate.getAlias());
                    }
                }
            }

            for (VisualizationSourceColumn select : query.getSelects())
                colMap.put(select.getOriginalName(), select.getAlias());
        }

        // Now that we have the full set of columns, we can take a pass through to eliminate the columns on the right
        // side of join clauses, since we know the columns contain duplicate data. We leave a key in the column map
        // for the originally requested column name, but replace the value column, so the requestor can use whichever
        // column name they like to find the results.
        Map<String, String> selectAliasRemapping = new HashMap<String, String>();
        for (VisualizationSourceQuery query : _sourceQueries.values())
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

    public UserSchema getPrimarySchema()
    {
        VisualizationSourceQuery firstQuery = _sourceQueries.values().iterator().next();
        return firstQuery.getSchema();
    }
}
