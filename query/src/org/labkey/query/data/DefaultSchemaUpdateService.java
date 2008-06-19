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
package org.labkey.query.data;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;

/*
* User: Dave
* Date: Jun 18, 2008
* Time: 11:13:42 AM
*/
public class DefaultSchemaUpdateService implements SchemaUpdateService
{
    private UserSchema _schema;

    public DefaultSchemaUpdateService(UserSchema schema)
    {
        _schema = schema;
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getSchemaName()
    {
        return _schema.getSchemaName();
    }

    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        TableInfo table = getSchema().getDbSchema().getTable(queryName);
        return (null != table && TableInfo.TABLE_TYPE_TABLE == table.getTableType())
                ? new DefaultQueryUpdateService(table)
                : null;
    }

    public void beginTransaction() throws SQLException
    {
        _schema.getDbSchema().getScope().beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        _schema.getDbSchema().getScope().commitTransaction();
    }

    public void rollbackTransaction()
    {
        _schema.getDbSchema().getScope().rollbackTransaction();
    }

    public boolean isTransactionActive()
    {
        return _schema.getDbSchema().getScope().isTransactionActive();
    }
}