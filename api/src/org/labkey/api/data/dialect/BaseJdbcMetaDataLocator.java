/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo.ImportedKey;
import org.labkey.api.data.DbScope;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:47 AM
 */

public class BaseJdbcMetaDataLocator implements JdbcMetaDataLocator
{
    private final DbScope _scope;
    private final String _schemaName;
    private final String _tableNamePattern;
    private final ConnectionHandler _connectionHandler;
    private final Connection _connection;
    private final DatabaseMetaData _dbmd;

    public BaseJdbcMetaDataLocator(DbScope scope, String schemaName, @Nullable String tableNamePattern, ConnectionHandler connectionHandler) throws SQLException
    {
        _scope = scope;
        _schemaName = schemaName;
        _tableNamePattern = tableNamePattern;
        _connectionHandler = connectionHandler;
        _connection = connectionHandler.getConnection();
        _dbmd = _connection.getMetaData();
    }

    @Override
    public DbScope getScope()
    {
        return _scope;
    }

    @Override
    public void close()
    {
        _connectionHandler.releaseConnection(_connection);
    }

    @Override
    public DatabaseMetaData getDatabaseMetaData()
    {
        return _dbmd;
    }

    @Override
    public String getCatalogName()
    {
        return _scope.getDatabaseName();
    }

    @Override
    public String getSchemaName()
    {
        return _schemaName;
    }

    @Override
    public String getTableName()
    {
        return _tableNamePattern;
    }

    @Override
    public String[] getTableTypes()
    {
        return _scope.getSqlDialect().getTableTypes();
    }

    @Override
    public ImportedKey getImportedKey(String fkName, String pkSchemaName, String pkTableName, String pkColumnName, String colName)
    {
        return new ImportedKey(fkName, pkSchemaName, pkTableName, pkColumnName, colName);
    }

    @Override
    public boolean supportsSchemas()
    {
        return true;
    }
}
