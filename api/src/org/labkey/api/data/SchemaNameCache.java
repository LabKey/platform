package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.JdbcMetaDataSelector.JdbcMetaDataResultSetFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * User: adam
 * Date: 7/7/2014
 * Time: 3:12 PM
 */
public class SchemaNameCache
{
    private static final SchemaNameCache INSTANCE = new SchemaNameCache();

    private final BlockingStringKeyCache<Map<String, String>> _cache = CacheManager.getBlockingStringKeyCache(50, CacheManager.YEAR, "Schema names in each scope", new CacheLoader<String, Map<String, String>>()
    {
        @Override
        public Map<String, String> load(String dsName, @Nullable Object argument)
        {
            DbScope scope = DbScope.getDbScope(dsName);

            try
            {
                return loadSchemaNameMap(scope);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    });

    private SchemaNameCache()
    {
    }

    public static SchemaNameCache get()
    {
        return INSTANCE;
    }

    /**
     * Returns a map of all schema names in this scope. The map has case-insensitive keys and values that are the canonical meta data names.
     */
    public Map<String, String> getSchemaNameMap(DbScope scope)
    {
        return _cache.get(scope.getDataSourceName());
    }

    // Query the JDBC metadata for all schemas in this database.
    private Map<String, String> loadSchemaNameMap(DbScope scope) throws SQLException
    {
        Connection conn = scope.getConnection();
        final Map<String, String> schemaNameMap = new CaseInsensitiveTreeMap<>();

        try
        {
            getSchemaNameSelector(scope, conn).forEach(new Selector.ForEachBlock<ResultSet>(){
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    String name = rs.getString(1).trim();
                    schemaNameMap.put(name, name);
                }
            });

            return Collections.unmodifiableMap(schemaNameMap);
        }
        finally
        {
            if (!scope.isTransactionActive())
                conn.close();
        }
    }

    private Selector getSchemaNameSelector(final DbScope scope, Connection conn) throws SQLException
    {
        return new JdbcMetaDataSelector(scope, conn, new JdbcMetaDataResultSetFactory()
        {
            @Override
            public ResultSet getResultSet(DbScope scope, DatabaseMetaData dbmd) throws SQLException
            {
                return scope.getSqlDialect().treatCatalogsAsSchemas() ? dbmd.getCatalogs() : dbmd.getSchemas();
            }
        });
    }
}
