package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 2/8/2015
 * Time: 7:47 AM
 */

// This works for all cases except MySQL and SQL Server synonyms
public class StandardJdbcMetaDataLocator implements JdbcMetaDataLocator
{
    private final DbScope _scope;
    private final Connection _conn;
    private final DatabaseMetaData _dbmd;

    private final String _schemaName;
    private final String _tableNamePattern;

    public StandardJdbcMetaDataLocator(DbScope scope, String schemaName, @Nullable String tableNamePattern) throws SQLException
    {
        _scope = scope;
        _conn = scope.getConnection();
        _dbmd = _conn.getMetaData();

        _schemaName = schemaName;
        _tableNamePattern = tableNamePattern;
    }

    @Override
    public DbScope getScope()
    {
        return _scope;
    }

    public Connection getConnection() throws SQLException
    {
        return _conn;
    }

    @Override
    public void close()
    {
        if (!_scope.isTransactionActive())
            _scope.releaseConnection(_conn);
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
}
