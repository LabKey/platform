/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.JdbcMetaDataSelector.JdbcMetaDataResultSetFactory;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;

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
        final Map<String, String> schemaNameMap = new CaseInsensitiveTreeMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getJdbcMetaDataLocator(scope, null, null))
        {
            JdbcMetaDataSelector selector = new JdbcMetaDataSelector(locator, (dbmd, locator1) -> {
                // Most dialects support schemas, but MySQL treats them as catalogs
                return locator1.supportsSchemas() ? dbmd.getSchemas() : dbmd.getCatalogs();
            });

            selector.forEach(rs -> {
                String name = rs.getString(1).trim();
                schemaNameMap.put(name, name);
            });
        }

        return Collections.unmodifiableMap(schemaNameMap);
    }
}
