/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.query.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpression;
import org.labkey.query.sql.Query;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.QueryDef;

import java.util.List;
import java.util.ArrayList;

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

    public StringExpression urlExpr(QueryAction action, Container container)
    {
        StringExpression expr = null;
        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo table = getTable(getSchema(), errors, true);
        if (table != null)
        {
            switch (action)
            {
                case detailsQueryRow:
                    expr = table.getDetailsURL(table.getColumnNameSet(), container);
                    break;
                
                case updateQueryRow:
                    expr = table.getUpdateURL(table.getColumnNameSet(), container);
                    break;
            }
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
                    StringBuilder sb = new StringBuilder(url.getLocalURIString());
                    for (String columnName : pkColumnNames)
                    {
                        sb.append("&").append(columnName).append("=${").append(columnName).append("}");
                    }
                    expr = StringExpressionFactory.create(sb.toString());
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
