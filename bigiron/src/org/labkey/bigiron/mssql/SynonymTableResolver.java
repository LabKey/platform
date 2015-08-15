package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.ForeignKeyResolver;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.StandardTableResolver;
import org.labkey.api.util.Pair;
import org.labkey.bigiron.mssql.SynonymManager.Synonym;
import org.labkey.bigiron.mssql.SynonymManager.SynonymForeignKeyResolver;
import org.labkey.bigiron.mssql.SynonymManager.SynonymJdbcMetaDataLocator;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by adam on 8/14/2015.
 */
public class SynonymTableResolver extends StandardTableResolver
{
    @Override
    public void addTableNames(Map<String, String> map, DbScope scope, String schemaName)
    {
        Map<String, Synonym> synonymMap = SynonymManager.getSynonymMap(scope, schemaName);

        for (String name : synonymMap.keySet())
            map.put(name, name);
    }

    @Override
    public JdbcMetaDataLocator getJdbcMetaDataLocator(DbScope scope, @Nullable String schemaName, @Nullable String tableName) throws SQLException
    {
        Pair<DbScope, Synonym> pair = SynonymManager.getSynonym(scope, schemaName, tableName);

        if (null == pair)
            return super.getJdbcMetaDataLocator(scope, schemaName, tableName);      // Not a valid synonym, so return the standard locator
        else
            return new SynonymJdbcMetaDataLocator(pair.first, scope, pair.second);  // tableName is a synonym, so return a synonym locator
    }

    @Override
    public ForeignKeyResolver getForeignKeyResolver(DbScope scope, @Nullable String schemaName, @Nullable String tableName)
    {
        Pair<DbScope, Synonym> pair = SynonymManager.getSynonym(scope, schemaName, tableName);

        if (null == pair)
            return super.getForeignKeyResolver(scope, schemaName, tableName);       // Not a synonym, so return the standard resolver
        else
            return new SynonymForeignKeyResolver(pair.first, scope, pair.second);   // tableName is a synonym, so return a synonym resolver
    }
}
