/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpression;
import org.labkey.query.sql.Query;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QueryDef;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class TableQueryDefinition extends QueryDefinitionImpl
{
    TableInfo _table;
    private String _sql;

    public TableQueryDefinition(UserSchema schema, String tableName)
    {
        super(getQueryDef(schema, tableName));
        _schema = schema;
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

    public ActionURL urlFor(QueryAction action, Container container)
    {
        ActionURL url = null;
        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo table = getTable(getSchema(), errors, true);
        if (table != null)
        {
            switch (action)
            {
                case insertQueryRow:
                    url = table.getInsertURL(container);
                    break;
                case executeQuery:
                    url = table.getGridURL(container);
                    break;
            }
        }

        return url != null ? url : super.urlFor(action, container);
    }

    public ActionURL urlFor(QueryAction action, Container container, Map<String, Object> pks)
    {
        ActionURL url = null;
        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo table = getTable(getSchema(), errors, true);
        if (table != null)
        {
            switch (action)
            {
                case detailsQueryRow:
                    Map<FieldKey, ColumnInfo> selectCols = QueryService.get().getColumns(table, Collections.<FieldKey>emptySet(), table.getColumns());
                    StringExpression expr = table.getDetailsURL(selectCols.keySet(), container);
                    // See if there's a details URL available with the set of columns that we can offer, and
                    // we have enough PK values to uniquely identify the row
                    if (expr != null && pks.keySet().containsAll(table.getPkColumnNames()))
                    {
                        Table.TableResultSet rs = null;
                        try
                        {
                            SimpleFilter filter = new SimpleFilter();
                            for (Map.Entry<String, Object> pk : pks.entrySet())
                            {
                                filter.addCondition(pk.getKey(), pk.getValue());
                            }
                            rs = Table.selectForDisplay(table, selectCols.values(), filter, null, 1, 0);
                            if (rs.next())
                            {
                                RenderContext ctx = new RenderContext(null);
                                ctx.setResultSet(rs, selectCols);
                                ctx.setRow(rs.getRowMap());
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
                        finally
                        {
                            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
                        }

                    }
                    break;
            }
        }

        return url != null ? url : super.urlFor(action, container, pks);
    }

    public StringExpression urlExpr(QueryAction action, Container container)
    {
        StringExpression expr = null;
        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo table = getTable(getSchema(), errors, true);
        if (table == null)
            return null;

        switch (action)
        {
            case detailsQueryRow:
                expr = table.getDetailsURL(Table.createFieldKeyMap(table).keySet(), container);
                break;

            case updateQueryRow:
                expr = table.getUpdateURL(Table.createFieldKeyMap(table).keySet(), container);
                break;
        }

        if (expr == null)
        {
            // XXX: is this the best place to create a generic query action expression url?
            ActionURL url = super.urlFor(action, container);
            if (url != null)
            {
                List<String> pkColumnNames = table.getPkColumnNames();
                if (pkColumnNames.size() > 0)
                {
                    Map<String, String> params = new HashMap<String, String>();
                    for (String columnName : pkColumnNames)
                    {
                        params.put(columnName, columnName);
                    }
                    return new DetailsURL(url, params);
                }
            }
        }

        return expr;
    }

    
    @Override
    public String getDescription()
    {
        TableInfo t = getTable(getSchema(), new ArrayList<QueryException>(), true);
        return t == null ? null : t.getDescription();
    }


    public TableInfo getTable(QuerySchema schema, List<QueryException> errors, boolean includeMetadata)
    {
        if (schema == getSchema())
        {
            if (_table == null)
                _table = schema.getTable(getName());
            if (_table != null)
            {
                return _table;
            }
        }
        return super.getTable(schema, errors, includeMetadata);
    }

    public String getSql()
    {
        if (_sql == null)
        {
            Query query = new Query(_schema);
            query.setRootTable(FieldKey.fromParts(getName()));
            _sql = query.getQueryText();
        }
        return _sql;
    }

    public void setSql(String sql)
    {
        // Can't change the SQL
    }

    public boolean isTableQueryDefinition()
    {
        return true;
    }

    public boolean isMetadataEditable()
    {
        TableInfo tableInfo = getTable(getSchema(), new ArrayList<QueryException>(), true);
        // Might have been deleted out from under us
        return tableInfo != null && tableInfo.isMetadataOverrideable();
    }
}
