/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TableQueryDefinition extends QueryDefinitionImpl
{
    private String _sql;
    private String _title;
    private static final Logger log = LogManager.getLogger(TableQueryDefinition.class);

    public TableQueryDefinition(UserSchema schema, String tableName)
    {
        super(schema.getUser(), schema.getContainer(), getQueryDef(schema, tableName));
        _schema = schema;

        assert MemTracker.getInstance().put(this);
    }


    private static QueryDef getQueryDef(UserSchema schema, String tableName)
    {
        QueryDef result = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), tableName, false);
        if (result == null)
        {
            result = new QueryDef();
            result.setName(tableName);
            result.setContainer(schema.getContainer().getId());
            result.setSchema(schema.getSchemaName());
        }
        return result;
    }


    @Override
    public ActionURL urlFor(QueryAction action, Container container)
    {
        ActionURL url = null;
        List<QueryException> errors = new ArrayList<>();
        TableInfo table = getTable(errors, true);
        if (table != null)
        {
            switch (action)
            {
                case insertQueryRow:
                    url = table.getInsertURL(container);
                    break;
                case deleteQueryRows:
                    url = table.getDeleteURL(container);
                    break;
                case executeQuery:
                    url = table.getGridURL(container);
                    break;
                case importData:
                    url = table.getImportDataURL(container);
                    break;
            }
        }

        if (url == AbstractTableInfo.LINK_DISABLER_ACTION_URL)
            return null;

        return url != null ? url : super.urlFor(action, container);
    }


    @Override
    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pks)
    {
        List<QueryException> errors = new ArrayList<>();
        TableInfo table = getTable(errors, true);
        if (table != null && table.hasPermission(getUser(), ReadPermission.class))
        {
            if (action == QueryAction.detailsQueryRow)
            {
                Map<FieldKey, ColumnInfo> selectCols = QueryService.get().getColumns(table, Collections.emptySet(), table.getColumns());
                StringExpression expr = table.getDetailsURL(selectCols.keySet(), container);
                // See if there's a details URL available with the set of columns that we can offer, and
                // we have enough PK values to uniquely identify the row
                if (expr != null && expr != AbstractTableInfo.LINK_DISABLER && pks.keySet().containsAll(table.getPkColumnNames()))
                {
                    SimpleFilter filter = new SimpleFilter();
                    for (Map.Entry<String, Object> pk : pks.entrySet())
                    {
                        filter.addCondition(pk.getKey(), pk.getValue());
                    }
                    try (Results results = new TableSelector(table, selectCols.values(), filter, null).setForDisplay(true).setMaxRows(1).getResults())
                    {
                        if (results.next())
                        {
                            RenderContext ctx = new RenderContext(ViewContext.getMockViewContext(getUser(), getContainer(), null, false));
                            ctx.setResults(results);
                            ctx.setRow(results.getRowMap());
                            return new ActionURL(expr.eval(ctx));
                        }
                        else
                        {
                            return null;
                        }
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            }
        }

        return super.urlFor(action, container, pks);
    }


    @Override
    public String getDescription()
    {
        if (null == _queryDef.getDescription())
        {
            TableInfo t = getTable(new ArrayList<>(), true);
            if (null != t)
                _queryDef.setDescription(t.getDescription());
        }
        return _queryDef.getDescription();
    }


    @Override
    public void setDescription(String description)
    {
        _queryDef.setDescription(description);
    }

    @Override
    public TableInfo createTable(@NotNull UserSchema schema, @Nullable List<QueryException> errors, boolean includeMetadata, @Nullable Query query, boolean skipSuggestedColumns)
    {
        try
        {
            return schema.getTable(getName(), getContainerFilter(), includeMetadata, false);
        }
        catch (QueryParseException e)
        {
            log.warn("Exception creating table '" + getName() + "': " + e.getMessage(), e);
            if (null != errors)
            {
                errors.add(new QueryParseException("Exception creating table '" + getName() + "': " + e.getMessage(), e, 0, 0));
                return null;
            }
            throw e;
        }
    }

    @Override
    public boolean canEdit(User user)
    {
        return false;
    }

    @Override
    public boolean canEditMetadata(User user)
    {
        return super.canEdit(getUser());
    }

    @Override
    public boolean canDelete(User user)
    {
        return false;
    }


    @Override
    public String getSql()
    {
        if (_sql != null)
            return _sql;

        UserSchema schema = getSchema();
        Query query = new Query(schema);
        query.setRootTable(FieldKey.fromParts(getName()));
        _sql = query.getQueryText();

        return _sql;
    }


    @Override
    public void setSql(String sql)
    {
        // Can't change the SQL
    }


    @Override
    public boolean isUserDefined()
    {
        return false;
    }


    @Override
    public boolean isSqlEditable()
    {
        return false;
    }


    @Override
    public boolean isMetadataEditable()
    {
        TableInfo tableInfo = getTable(new ArrayList<>(), true);
        // Might have been deleted out from under us
        return tableInfo != null && tableInfo.isMetadataOverrideable();
    }

    @Override
    public String getTitle()
    {
        if (null == _title)
        {
            List<QueryException> errors = new ArrayList<>();
            TableInfo tableInfo = getTable(getSchema(), errors, false);
            if (null != tableInfo && null != tableInfo.getName())
                _title = tableInfo.getTitleField();
        }

        if (null == _title)
            _title = getName();
        return _title;
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent)
    {
        if (getMetadataXml() == null)
        {
            QueryDef qdef = QueryManager.get().getQueryDef(container, getSchemaName(), getName(), false);
            if (qdef != null)
            {
                // delete the query in order to reset the metadata over a built-in query, but don't
                // fire the listener because we haven't actually deleted the table. See issue 40365
                QueryManager.get().delete(qdef);
            }
            return Collections.emptyList();
        }
        else
        {
            return super.save(user, container, fireChangeEvent);
        }
    }
}
