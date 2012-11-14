/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.VisualizationController;

import java.util.*;

/**
 * User: brittp
 * Date: Jan 27, 2011 10:51:03 AM
 */
public abstract class VisualizationProvider
{
    private VisualizationSQLGenerator.ChartType _type;

    public abstract List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>
            getJoinColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery first, IVisualizationSourceQuery second, boolean isGroupByQuery);
    public abstract void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery query);

    public abstract void appendAggregates(StringBuilder sql, Map<String, Set<VisualizationSourceColumn>> columnAliases, Map<String, VisualizationIntervalColumn> intervals, String queryAlias, IVisualizationSourceQuery joinQuery);

    public abstract boolean isJoinColumn(VisualizationSourceColumn column, Container container);
    
    protected static enum ColumnMatchType
    {
        DATETIME_COLS()
                {
                    @Override
                    public boolean match(ColumnInfo col)
                    {
                        return !col.isHidden() && col.isDateTimeType();
                    }
                },
        CONFIGURED_MEASURES()
                {
                    @Override
                    public boolean match(ColumnInfo col)
                    {
                        return !col.isHidden() && col.isMeasure();
                    }
                },
        CONFIGURED_DIMENSIONS()
                {
                    @Override
                    public boolean match(ColumnInfo col)
                    {
                        return !col.isHidden() && col.isDimension();
                    }
                },
        All_VISIBLE()
                {
                    @Override
                    public boolean match(ColumnInfo col)
                    {
                        return !col.isHidden();
                    }
                },
        All()
                {
                    @Override
                    public boolean match(ColumnInfo col)
                    {
                        return true;
                    }
                };


        public abstract boolean match(ColumnInfo col);
    }

    private String _schemaName;

    public VisualizationProvider(String userSchemaName)
    {
        _schemaName = userSchemaName;
    }

    protected UserSchema getUserSchema(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, _schemaName);
        if (schema == null)
            throw new IllegalStateException("No schema found with name " + _schemaName);
        return schema;
    }

    protected Pair<QueryDefinition, TableInfo> getQueryDefinition(ViewContext context, ColumnMatchType matchType, String queryName)
    {
        UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
        return getTableAndQueryDef(context, schema, queryName, matchType, !schema.getTableNames().contains(queryName));
    }

    protected Pair<QueryDefinition, TableInfo> getTableAndQueryDef(ViewContext context, UserSchema schema, String queryName, ColumnMatchType matchType, boolean customQuery)
    {
        QueryDefinition queryDef;
        if (customQuery)
            queryDef = QueryService.get().getQueryDef(context.getUser(), context.getContainer(), _schemaName, queryName);
        else
            queryDef = QueryService.get().createQueryDefForTable(schema, queryName);
        if (queryDef != null && !queryDef.isHidden())
        {
            List<QueryException> errors = new ArrayList<QueryException>();
            TableInfo table = queryDef.getTable(errors, true);
            if (table != null)
            {
                if (isValid(table, queryDef, matchType))
                    return new Pair<QueryDefinition, TableInfo>(queryDef, table);
            }
        }
        return null;
    }

    protected Map<QueryDefinition, TableInfo> getQueryDefinitions(ViewContext context, VisualizationController.QueryType queryType, ColumnMatchType matchType)
    {
        Map<QueryDefinition, TableInfo> queries = new HashMap<QueryDefinition, TableInfo>();
        UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
        if (queryType == VisualizationController.QueryType.all || queryType == VisualizationController.QueryType.custom)
        {
            Map<String, QueryDefinition> queryDefMap = schema.getQueryDefs();
            for (Map.Entry<String, QueryDefinition> entry : queryDefMap.entrySet())
            {
                QueryDefinition qdef = entry.getValue();
                if (!qdef.isHidden())
                {
                    List<QueryException> errors = new ArrayList<QueryException>();
                    TableInfo table = qdef.getTable(schema, errors, true);
                    // Ignore specific errors- failed queries won't produce a table
                    if (table != null && isValid(table, qdef, matchType))
                        queries.put(qdef, table);
                }
            }
        }

        // built in tables
        if (queryType == VisualizationController.QueryType.all || queryType == VisualizationController.QueryType.builtIn)
        {
            for (String name : getTableNames(schema))
            {
                Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(context, schema, name, matchType, false);
                if (entry != null)
                    queries.put(entry.getKey(), entry.getValue());
            }
        }
        return queries;
    }

    protected Set<String> getTableNames(UserSchema schema)
    {
        return schema.getTableNames();
    }


    protected boolean isValid(TableInfo info, QueryDefinition query, ColumnMatchType type)
    {
        return true;
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(Container container, Map<QueryDefinition, TableInfo> queries, ColumnMatchType columnMatchType)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> matches = new HashMap<Pair<FieldKey, ColumnInfo>, QueryDefinition>();
        for (Map.Entry<QueryDefinition, TableInfo> entry : queries.entrySet())
        {
            QueryDefinition query = entry.getKey();
            TableInfo table = entry.getValue();

            Map<FieldKey, ColumnInfo> columns;
            if (columnMatchType.equals(ColumnMatchType.All))
                columns = table.getExtendedColumns(true);
            else
                columns = table.getExtendedColumns(false);

            for (ColumnInfo col : columns.values())
            {
                // ignore hidden columns
                if (columnMatchType.match(col))
                {
                    if (col.getFk() != null)
                    {
                        ColumnInfo lookupCol = col.getFk().createLookupColumn(col, null);
                        if (lookupCol != null)
                        {
                            lookupCol.setLabel(col.getLabel());
                            col = lookupCol;
                        }
                    }

                    matches.put(Pair.of(col.getFieldKey(), col), query);
                }
            }
        }
        return matches;
    }

    public void configure(VisualizationSQLGenerator.ChartType type)
    {
        _type = type;
    }

    public VisualizationSQLGenerator.ChartType getType()
    {
        return _type;
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(ViewContext context, ColumnMatchType matchType, String queryName)
    {
        Pair<QueryDefinition, TableInfo> queryDef = getQueryDefinition(context, matchType, queryName);
        if (queryDef != null)
            return getMatchingColumns(context.getContainer(), Collections.singletonMap(queryDef.getKey(), queryDef.getValue()), matchType);
        else
            return Collections.emptyMap();
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(ViewContext context, VisualizationController.QueryType queryType, ColumnMatchType matchType)
    {
        Map<QueryDefinition, TableInfo> queries = getQueryDefinitions(context, queryType, matchType);
        return getMatchingColumns(context.getContainer(), queries, matchType);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        return getMatchingColumns(context, queryType, ColumnMatchType.CONFIGURED_MEASURES);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.CONFIGURED_MEASURES, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        return getMatchingColumns(context, queryType, ColumnMatchType.DATETIME_COLS);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDateMeasures(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.DATETIME_COLS, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getZeroDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        // By default, assume that any date can be a measure date or a zero date.
        return getDateMeasures(context, queryType);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDimensions(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.CONFIGURED_DIMENSIONS, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(ViewContext context, String queryName, boolean hiddenColumns)
    {
        return getMatchingColumns(context, hiddenColumns ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(ViewContext context, VisualizationController.QueryType queryType, boolean hiddenColumns)
    {
        return getMatchingColumns(context, queryType, hiddenColumns ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE);
    }
}
