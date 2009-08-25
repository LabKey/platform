/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.query.data;

import org.labkey.query.view.DbUserSchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.DefaultSchemaUpdateService;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

/*
* User: Dave
* Date: Oct 20, 2008
* Time: 1:00:51 PM
*/

/**
 * The SchemaUpdateService for DbUserSchemas
 */
public class DbUserSchemaUpdateService extends DefaultSchemaUpdateService
{
    public DbUserSchemaUpdateService(String schemaName)
    {
        super(schemaName);
    }

    public DbUserSchema getUserSchema(Container container, User user)
    {
        return (DbUserSchema)getSchema(container, user);
    }

    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        DbUserSchema schema = getUserSchema(container, user);
        TableInfo table = schema.getDbSchema().getTable(queryName);
        if (null == table || TableInfo.TABLE_TYPE_TABLE != table.getTableType())
            return null;
        if (null == table.getPkColumnNames() || table.getPkColumnNames().size() == 0)
            throw new RuntimeException("The table '" + queryName + "' does not have a primary key defined, and thus cannot be updated reliably. Please define a primary key for this table before attempting an update.");
        
        return new DbUserQueryUpdateService(table, schema.getDbContainer());
    }
}