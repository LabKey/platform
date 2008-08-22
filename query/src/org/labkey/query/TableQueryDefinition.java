/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.query.data.Query;

import java.util.List;

public class TableQueryDefinition extends QueryDefinitionImpl
{
    TableInfo _table;

    public TableQueryDefinition(UserSchema schema, String tableName, TableInfo table)
    {
        super(schema, tableName);
        _table = table;
        Query query = new Query(schema);
        query.setRootTable(FieldKey.fromParts(tableName));
        setSql(query.getQueryText());
    }

    public TableInfo getTable(String name, QuerySchema schema, List<QueryException> errors)
    {
        if (_table != null && schema == getSchema() && name == null)
        {
            return _table;
        }
        return super.getTable(name, schema, errors);
    }

    public boolean canEdit(User user)
    {
        return false;
    }
}
