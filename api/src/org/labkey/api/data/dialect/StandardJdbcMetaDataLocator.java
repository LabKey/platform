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
public class StandardJdbcMetaDataLocator implements JdbcMetaDataLocator
{
    private final DbScope _scope;
    private final Connection _connection;
    private final DatabaseMetaData _dbmd;

    private final String _schemaName;
    private final String _schemaNamePattern;
    private final String _tableName;
    private final String _tableNamePattern;

    public StandardJdbcMetaDataLocator(DbScope scope, String schemaName, String schemaNamePattern, String tableName, String tableNamePattern) throws SQLException
    {
        _scope = scope;
        _connection = getConnection();
        _dbmd = _connection.getMetaData();

        _schemaName = schemaName;
        _schemaNamePattern = schemaNamePattern;
        _tableName = tableName;
        _tableNamePattern = tableNamePattern;
    }

    protected Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    @Override
    public DbScope getScope()
    {
        return _scope;
    }

    @Override
    public void close() throws SQLException
    {
        _connection.close();
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
        if (null == _schemaName)
            throw new IllegalStateException("Schema name has not been initialized!");
        return _schemaName;
    }

    @Override
    public String getSchemaNamePattern()
    {
        if (null == _schemaNamePattern)
            throw new IllegalStateException("Schema name pattern has not been initialized!");
        return _schemaNamePattern;
    }

    @Override
    public String getTableName()
    {
        if (null == _tableName)
            throw new IllegalStateException("Table name has not been initialized!");
        return _tableName;
    }

    @Override
    public String getTableNamePattern()
    {
        if (null == _tableNamePattern)
            throw new IllegalStateException("Table name pattern has not been initialized!");
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
