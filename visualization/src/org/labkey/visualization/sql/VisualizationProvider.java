package org.labkey.visualization.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
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
* Date: Jan 27, 2011 10:51:03 AM
*/
public abstract class VisualizationProvider
{
    public abstract List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>>
    getJoinColumns(VisualizationSourceColumn.Factory factory, VisualizationSourceQuery first, VisualizationSourceQuery second);

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
                    }};

        public abstract boolean match(ColumnInfo col);
    }

    private String _schemaName;

    public VisualizationProvider(String userSchemaName)
    {
        _schemaName = userSchemaName;
    }

    protected UserSchema getUserSchema(Container container, User user)
    {
        DefaultSchema defSchema = DefaultSchema.get(user, container);
        QuerySchema schema = defSchema.getSchema(_schemaName);
        if (!(schema instanceof UserSchema))
        {
            if (schema == null)
                throw new IllegalStateException("No schema found with name " + _schemaName);
            else
                throw new IllegalStateException("Unexpected schema type: " + schema.getClass().getSimpleName());
        }
        return (UserSchema) schema;
    }

    protected QueryView getQueryView(ViewContext context, ColumnMatchType matchType, String queryName)
    {
        UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
        // built in tables
        if (schema.getTableNames().contains(queryName))
        {
            QueryView view = getView(context, schema, queryName);
            if (isValid(view, matchType))
                return view;
        }

        // custom queries:
        QueryDefinition qdef = QueryService.get().getQueryDef(context.getUser(), context.getContainer(), _schemaName, queryName);
        if (qdef != null && !qdef.isHidden())
        {
            QueryView view = getView(context, schema, qdef.getName());
            if (isValid(view, matchType))
                return view;
        }
        return null;
    }

    protected Collection<QueryView> getQueryViews(ViewContext context, VisualizationController.QueryType queryType, ColumnMatchType matchType)
    {
        Map<String, QueryView> views = new HashMap<String, QueryView>();
        UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
        if (queryType == VisualizationController.QueryType.all || queryType == VisualizationController.QueryType.custom)
        {
            Map<String, QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(context.getUser(), context.getContainer(), _schemaName);
            for (Map.Entry<String, QueryDefinition> entry : queryDefMap.entrySet())
            {
                QueryDefinition qdef = entry.getValue();
                if (!qdef.isHidden())
                {
                    QueryView view = getView(context, schema, qdef.getName());
                    if (isValid(view, matchType))
                        views.put(qdef.getName(), view);
                }
            }
        }

        // built in tables
        if (queryType == VisualizationController.QueryType.all || queryType == VisualizationController.QueryType.builtIn)
        {
            for (String name : schema.getTableNames())
            {
                QueryView view = getView(context, schema, name);
                if (isValid(view, matchType))
                    views.put(name, view);
            }
        }
        return views.values();
    }

    protected boolean isValid(QueryView view, ColumnMatchType type)
    {
        return true;
    }

    protected QueryView getView(ViewContext context, UserSchema schema, String queryName)
    {
        QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName);
        return new QueryView(schema, settings, null);
    }

    protected Map<ColumnInfo, QueryView> getMatchingColumns(Container container, Collection<QueryView> views, ColumnMatchType columnMatchType)
    {
        Map<ColumnInfo, QueryView> matches = new HashMap<ColumnInfo, QueryView>();
        for (QueryView view : views)
        {
            for (DisplayColumn dc : view.getDisplayColumns())
            {
                ColumnInfo col = dc.getColumnInfo();

                if (col != null)
                {
                    // ignore hidden columns
                    if (columnMatchType.match(col))
                    {
                        if (col.getFk() != null)
                        {
                            ColumnInfo lookupCol = col.getFk().createLookupColumn(col, null);
                            if (lookupCol != null)
                                col = lookupCol;
                        }
                        
                        matches.put(col, view);
                    }
                }
            }
        }
        return matches;
    }

    protected Map<ColumnInfo, QueryView> getMatchingColumns(ViewContext context, ColumnMatchType matchType, String queryName)
    {
        QueryView view = getQueryView(context, matchType, queryName);
        if (view != null)
            return getMatchingColumns(context.getContainer(), Collections.singleton(view), matchType);
        else
            return Collections.emptyMap();
    }

    protected Map<ColumnInfo, QueryView> getMatchingColumns(ViewContext context, VisualizationController.QueryType queryType, ColumnMatchType matchType)
    {
        Collection<QueryView> views = getQueryViews(context, queryType, matchType);
        return getMatchingColumns(context.getContainer(), views, matchType);
    }

    public Map<ColumnInfo, QueryView> getMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        return getMatchingColumns(context, queryType, ColumnMatchType.CONFIGURED_MEASURES);
    }

    public Map<ColumnInfo, QueryView> getMeasures(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.CONFIGURED_MEASURES, queryName);
    }

    public Map<ColumnInfo, QueryView> getDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        return getMatchingColumns(context, queryType, ColumnMatchType.DATETIME_COLS);
    }

    public Map<ColumnInfo, QueryView> getDateMeasures(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.DATETIME_COLS, queryName);
    }

    public Map<ColumnInfo, QueryView> getZeroDateMeasures(ViewContext context, VisualizationController.QueryType queryType)
    {
        // By default, assume that any date can be a measure date or a zero date.
        return getDateMeasures(context, queryType);
    }

    public Map<ColumnInfo, QueryView> getDimensions(ViewContext context, String queryName)
    {
        return getMatchingColumns(context, ColumnMatchType.CONFIGURED_DIMENSIONS, queryName);
    }
}
