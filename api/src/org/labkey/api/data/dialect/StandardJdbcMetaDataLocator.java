/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:47 AM
 */

// This works for all cases except MySQL and SQL Server synonyms
public class StandardJdbcMetaDataLocator extends BaseJdbcMetaDataLocator
{
    // Use a transaction to share the connection... revisit once we share connections by default
    public StandardJdbcMetaDataLocator(DbScope scope, String schemaName, @Nullable String tableNamePattern, Transaction transaction) throws SQLException
    {
        super(scope, schemaName, tableNamePattern, new ConnectionHandler()
        {
            @Override
            public Connection getConnection()
            {
                return transaction.getConnection();
            }

            @Override
            public void releaseConnection(Connection conn)
            {
                transaction.commit();
                transaction.close();
            }
        });
    }

    public StandardJdbcMetaDataLocator(DbScope scope, String schemaName, @Nullable String tableNamePattern) throws SQLException
    {
        this(scope, schemaName, tableNamePattern, scope.ensureTransaction());
    }
}
