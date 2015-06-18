/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.api.visualization;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 27, 2011 10:51:03 AM
 */
public abstract class VisualizationProvider<SchemaType extends UserSchema>
{
    private ChartType _type;

    public abstract List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>
            getJoinColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery first, IVisualizationSourceQuery second, boolean isGroupByQuery);

    public abstract void addExtraSelectColumns(VisualizationSourceColumn.Factory factory, IVisualizationSourceQuery query);

    public abstract void appendAggregates(StringBuilder sql, Map<String, Set<VisualizationSourceColumn>> columnAliases, Map<String, VisualizationIntervalColumn> intervals, String queryAlias, IVisualizationSourceQuery joinQuery);

    /** @return true if the column is one that is needed to correctly join between separate queries */
    public abstract boolean isJoinColumn(VisualizationSourceColumn column, Container container);

    public abstract void addExtraResponseProperties(Map<String, Object> extraProperties);

    public abstract void addExtraColumnProperties(ColumnInfo column, TableInfo table, Map<String, Object> props);

    public abstract String getSourceCountSql(@NotNull JSONArray sources, JSONArray members, String colName);

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

    private final SchemaType _schema;

    public VisualizationProvider(SchemaType schema)
    {
        _schema = schema;
    }

    public SchemaType getSchema()
    {
        return _schema;
    }

    protected Pair<QueryDefinition, TableInfo> getQueryDefinition(ColumnMatchType matchType, String queryName)
    {
        return getTableAndQueryDef(queryName, matchType, !_schema.getTableNames().contains(queryName));
    }

    protected Pair<QueryDefinition, TableInfo> getTableAndQueryDef(String queryName, ColumnMatchType matchType, boolean customQuery)
    {
        QueryDefinition queryDef;
        if (customQuery)
            queryDef = QueryService.get().getQueryDef(_schema.getUser(), _schema.getContainer(), _schema.getSchemaName(), queryName);
        else
            queryDef = QueryService.get().createQueryDefForTable(_schema, queryName);
        if (queryDef != null && !queryDef.isHidden())
        {
            List<QueryException> errors = new ArrayList<>();
            TableInfo table = queryDef.getTable(errors, true);
            if (table != null)
            {
                if (isValid(table, queryDef, matchType))
                    return new Pair<>(queryDef, table);
            }
        }
        return null;
    }

    protected Map<QueryDefinition, TableInfo> getQueryDefinitions(QueryType queryType, ColumnMatchType matchType)
    {
        Map<QueryDefinition, TableInfo> queries = new HashMap<>();
        if (queryType == QueryType.all || queryType == QueryType.custom)
        {
            Map<String, QueryDefinition> queryDefMap = _schema.getQueryDefs();
            for (Map.Entry<String, QueryDefinition> entry : queryDefMap.entrySet())
            {
                QueryDefinition qdef = entry.getValue();
                if (!qdef.isHidden())
                {
                    List<QueryException> errors = new ArrayList<>();
                    TableInfo table = qdef.getTable(_schema, errors, true);
                    // Ignore specific errors- failed queries won't produce a table
                    if (table != null && isValid(table, qdef, matchType))
                        queries.put(qdef, table);
                }
            }
        }

        // built in tables
        if (queryType == QueryType.all || queryType == QueryType.builtIn)
        {
            for (String name : getTableNames(_schema))
            {
                Pair<QueryDefinition, TableInfo> entry = getTableAndQueryDef(name, matchType, false);
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

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(Map<QueryDefinition, TableInfo> queries, ColumnMatchType columnMatchType)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> matches = new HashMap<>();
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
                    matches.put(Pair.of(col.getFieldKey(), col), query);
                }
            }
        }
        return matches;
    }

    public void configure(ChartType type)
    {
        _type = type;
    }

    public ChartType getType()
    {
        return _type;
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(ColumnMatchType matchType, String queryName)
    {
        Pair<QueryDefinition, TableInfo> queryDef = getQueryDefinition(matchType, queryName);
        if (queryDef != null)
            return getMatchingColumns(Collections.singletonMap(queryDef.getKey(), queryDef.getValue()), matchType);
        else
            return Collections.emptyMap();
    }

    protected Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMatchingColumns(QueryType queryType, ColumnMatchType matchType)
    {
        Map<QueryDefinition, TableInfo> queries = getQueryDefinitions(queryType, matchType);
        return getMatchingColumns(queries, matchType);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(QueryType queryType)
    {
        return getMatchingColumns(queryType, ColumnMatchType.CONFIGURED_MEASURES);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(String queryName)
    {
        return getMatchingColumns(ColumnMatchType.CONFIGURED_MEASURES, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDateMeasures(QueryType queryType)
    {
        return getMatchingColumns(queryType, ColumnMatchType.DATETIME_COLS);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDateMeasures(String queryName)
    {
        return getMatchingColumns(ColumnMatchType.DATETIME_COLS, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getZeroDateMeasures(QueryType queryType)
    {
        // By default, assume that any date can be a measure date or a zero date.
        return getDateMeasures(queryType);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDimensions(String queryName)
    {
        return getMatchingColumns(ColumnMatchType.CONFIGURED_DIMENSIONS, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(String queryName, boolean hiddenColumns)
    {
        return getMatchingColumns(hiddenColumns ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE, queryName);
    }

    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getAllColumns(QueryType queryType, boolean hiddenColumns)
    {
        return getMatchingColumns(queryType, hiddenColumns ? ColumnMatchType.All : ColumnMatchType.All_VISIBLE);
    }

    public static enum ChartType
    {
        TIME_DATEBASED,
        TIME_VISITBASED
    }

    public enum QueryType {
        builtIn,
        custom,
        datasets,
        all
    }

    public static class MeasureSetRequest
    {
        private String[] _filters = new String[0];
        private String _schemaName;
        private String _queryName;
        private String _name;
        private boolean _dateMeasures;
        private boolean _zeroDateMeasures;
        private boolean _allColumns;
        private boolean _showHidden;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String[] getFilters()
        {
            return _filters;
        }

        public void setFilters(String[] filters)
        {
            _filters = filters;
        }

        public boolean isDateMeasures()
        {
            return _dateMeasures;
        }

        public void setDateMeasures(boolean dateMeasures)
        {
            _dateMeasures = dateMeasures;
        }

        public boolean isZeroDateMeasures()
        {
            return _zeroDateMeasures;
        }

        public void setZeroDateMeasures(boolean zeroDateMeasures)
        {
            _zeroDateMeasures = zeroDateMeasures;
        }

        public boolean isAllColumns()
        {
            return _allColumns;
        }

        public void setAllColumns(boolean allColumns)
        {
            _allColumns = allColumns;
        }

        public boolean isShowHidden()
        {
            return _showHidden;
        }

        public void setShowHidden(boolean showHidden)
        {
            _showHidden = showHidden;
        }

        public String getCacheKey()
        {
            ArrayList<String> strs = new ArrayList<>(14);
            strs.add(String.valueOf(_schemaName));
            strs.add(String.valueOf(_queryName));
            strs.add(String.valueOf(_name));
            strs.add(String.valueOf(_dateMeasures));
            strs.add(String.valueOf(_zeroDateMeasures));
            strs.add(String.valueOf(_dateMeasures));
            strs.add(String.valueOf(_allColumns));
            strs.add(String.valueOf(_showHidden));
            strs.addAll(Arrays.asList(_filters));
            return StringUtils.join(strs,'|');
        }
    }

    public static class MeasureFilter
    {
        private String _schema;
        private String _query;
        private VisualizationProvider.QueryType _queryType = VisualizationProvider.QueryType.all;

        public MeasureFilter(String filter)
        {
            parse(filter);
        }

        protected void parse(String filter)
        {
            String[] parts = filter.split("\\|");

            assert(parts.length >= 2) : "Invalid filter value";

            _schema = parts[0];

            if (!parts[1].equals("~"))
                _query = parts[1];

            if (parts.length >= 3)
                _queryType = VisualizationProvider.QueryType.valueOf(parts[2]);
        }

        public String getSchema()
        {
            return _schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public VisualizationProvider.QueryType getQueryType()
        {
            return _queryType;
        }
    }
}
