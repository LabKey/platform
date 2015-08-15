package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by adam on 8/14/2015.
 */
public class StandardTableResolver implements TableResolver
{
    // Do nothing by default
    @Override
    public void addTableNames(Map<String, String> map, DbScope scope, String schemaName)
    {
    }

    @Override
    public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, @Nullable String schemaName, @Nullable String tableName) throws SQLException
    {
        return new StandardJdbcMetaDataLocator(scope, schemaName, tableName);
    }

    private static final ForeignKeyResolver STANDARD_RESOLVER = new StandardForeignKeyResolver();

    @Override
    public ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName)
    {
        return STANDARD_RESOLVER;
    }
}
