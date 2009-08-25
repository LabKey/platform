/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;

/*
* User: Dave
* Date: Jun 18, 2008
* Time: 11:13:42 AM
*/
public class DefaultSchemaUpdateService implements SchemaUpdateService
{
    private String _schemaName;

    public DefaultSchemaUpdateService(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public DbSchema getDbSchema()
    {
        return DbSchema.get(getSchemaName());
    }

    public QuerySchema getSchema(Container container, User user)
    {
        return QueryService.get().getUserSchema(user, container, getSchemaName());
    }

    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        QuerySchema schema = getSchema(container, user);
        TableInfo table = schema.getTable(queryName);
        return (null != table && TableInfo.TABLE_TYPE_TABLE == table.getTableType())
                ? new DefaultQueryUpdateService(table)
                : null;
    }

    public void beginTransaction() throws SQLException
    {
        getDbSchema().getScope().beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        getDbSchema().getScope().commitTransaction();
    }

    public void rollbackTransaction()
    {
        getDbSchema().getScope().rollbackTransaction();
    }

    public boolean isTransactionActive()
    {
        return getDbSchema().getScope().isTransactionActive();
    }

    public String getDomainURI(String queryName, Container container, User user)
    {
        return null;
    }
}