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
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
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

    @Override
    public String getDescription()
    {
        return getTable(getSchema(), new ArrayList<QueryException>(), true).getDescription();
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
        return getTable(getSchema(), new ArrayList<QueryException>(), true).isMetadataOverrideable();
    }
}
