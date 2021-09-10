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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo.ImportedKey;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;

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

    private String _schemaName;
    private String _schemaNamePattern;
    private String _tableName;
    private String _tableNamePattern;

    public StandardJdbcMetaDataLocator(DbScope scope) throws SQLException
    {
        _scope = scope;
        _connection = getConnection();
        _dbmd = _connection.getMetaData();
    }

    protected Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    @Override
    public JdbcMetaDataLocator singleSchema(@NotNull String schemaName)
    {
        _schemaName = schemaName;
        _schemaNamePattern = escapeName(schemaName);
        return this;
    }

    @Override
    public JdbcMetaDataLocator allSchemas()
    {
        _schemaName = null;
        _schemaNamePattern = "%";
        return this;
    }

    @Override
    public JdbcMetaDataLocator singleTable(@NotNull String tableName) throws SQLException
    {
        _tableName = tableName;
        _tableNamePattern = escapeName(tableName);
        return this;
    }

    @Override
    public JdbcMetaDataLocator singleTable(@NotNull SchemaTableInfo tableInfo) throws SQLException
    {
        return singleTable(tableInfo.getMetaDataName());
    }

    @Override
    public JdbcMetaDataLocator allTables()
    {
        _tableName = null;
        _tableNamePattern = "%";
        return this;
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
            throw new IllegalStateException("Schema setting method has not been called");
        return _schemaName;
    }

    @Override
    public String getSchemaNamePattern()
    {
        if (null == _schemaNamePattern)
            throw new IllegalStateException("Schema setting method has not been called");
        return _schemaNamePattern;
    }

    @Override
    public String getTableName()
    {
        if (null == _tableName)
            throw new IllegalStateException("Table setting method has not been called");
        return _tableName;
    }

    @Override
    public String getTableNamePattern()
    {
        if (null == _tableNamePattern)
            throw new IllegalStateException("Table setting method has not been called");
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

    // We must escape LIKE wild card characters in cases where we're passing a single table or schema name as a pattern
    // parameter, see #43821
    private static String escapeName(@NotNull String name)
    {
        String ret = name.replace("\\", "\\\\");
        ret = ret.replace("_", "\\_");
        ret = ret.replace("%", "\\%");

        return ret;
    }
}
