/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

public class SchemaNameCache
{
    private static final SchemaNameCache INSTANCE = new SchemaNameCache();

    private final BlockingCache<String, Map<String, String>> _cache = CacheManager.getBlockingStringKeyCache(500, CacheManager.YEAR, "Schema names in each scope", (dsName, argument) -> {
        DbScope scope = DbScope.getDbScope(dsName);

        try
        {
            return loadSchemaNameMap(scope);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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
     * Returns a map of all schema names in this scope. The map has case-insensitive keys and values that are the canonical metadata names.
     */
    public Map<String, String> getSchemaNameMap(DbScope scope)
    {
        return _cache.get(scope.getDataSourceName());
    }

    public void remove(DbScope scope)
    {
        _cache.remove(scope.getDataSourceName());
    }

    // Query the JDBC metadata for all schemas in this database.
    private Map<String, String> loadSchemaNameMap(DbScope scope) throws SQLException
    {
        final Map<String, String> schemaNameMap = new CaseInsensitiveTreeMap<>();

        try (JdbcMetaDataLocator locator = scope.getSqlDialect().getTableResolver().getAllSchemasLocator(scope))
        {
            JdbcMetaDataSelector selector = new JdbcMetaDataSelector(locator, (dbmd, locator1) -> scope.getSchemas(dbmd));

            selector.forEach(rs -> {
                String name = rs.getString(1).trim();
                schemaNameMap.put(name, name);
            });
        }

        return Collections.unmodifiableMap(schemaNameMap);
    }
}
