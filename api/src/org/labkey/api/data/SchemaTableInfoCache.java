/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.data.DbScope.SchemaTableOptions;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.logging.LogHelper;

import java.sql.SQLException;

public class SchemaTableInfoCache
{
    private static final Logger LOG = LogHelper.getLogger(SchemaTableInfoCache.class, "Loading of schema and table metadata from database schemas");

    private final BlockingCache<String, SchemaTableInfo> _blockingCache;

    public SchemaTableInfoCache(DbScope scope, boolean provisioned)
    {
        _blockingCache = createCache(scope, provisioned);
        _blockingCache.setCacheTimeChooser(TABLE_CACHE_TIME_CHOOSER);
    }

    // Ask the DbSchemaType how long to cache each table
    @SuppressWarnings("DataFlowIssue")
    private static final CacheTimeChooser<String> TABLE_CACHE_TIME_CHOOSER = (key, argument) -> ((SchemaTableOptions)argument).getSchema().getType().getCacheTimeToLive();

    /** This is expensive... use only for debugging and troubleshooting */
    public <OptionType extends SchemaTableOptions> boolean isCached(@NotNull OptionType options)
    {
        DbSchema schema = options.getSchema();
        String key = getCacheKey(schema.getName(), options.getTableName(), schema.getType());
        return _blockingCache.getKeys().contains(key);
    }

    <OptionType extends SchemaTableOptions> SchemaTableInfo get(@NotNull OptionType options)
    {
        DbSchema schema = options.getSchema();
        String key = getCacheKey(schema.getName(), options.getTableName(), schema.getType());
        return _blockingCache.get(key, options);
    }

    void remove(@NotNull String schemaName, @NotNull String tableName, @NotNull DbSchemaType type)
    {
        LOG.debug("remove " + type + " schema table: " + schemaName + "." + tableName);
        String key = getCacheKey(schemaName, tableName, type);
        _blockingCache.remove(key);
    }

    void removeAllTables(@NotNull String schemaName, @NotNull DbSchemaType type)
    {
        LOG.debug("remove all " + type + " schema tables: " + schemaName);
        final String prefix = type.getCacheKey(schemaName);

        // Note: A single database schema could be cached under multiple types, e.g., Module, Bare, Fast
        _blockingCache.removeUsingFilter(new Cache.StringPrefixFilter(prefix));
    }

    private String getCacheKey(@NotNull String schemaName, @NotNull String tableName, @NotNull DbSchemaType type)
    {
        return type.getCacheKey(schemaName) + "|" + tableName.toLowerCase();
    }

    private static class SchemaTableLoader implements CacheLoader<String, SchemaTableInfo>
    {
        @Override
        public SchemaTableInfo load(@NotNull String key, Object argument)
        {
            SchemaTableOptions options = (SchemaTableOptions)argument;
            String fullName = options.getSchema().getName() + "." + options.getTableName();

            try
            {
                LOG.debug("loading schema table: " + fullName);
                return options.getSchema().loadTable(options.getTableName(), options);
            }
            catch (SQLException e)
            {
                // Issue 49506: Log all problems for the admin and report to mothership and throw instead of returning null.
                LOG.warn("Exception while attempting to load schema table \"" + fullName + "\"", e);
                ExceptionUtil.logExceptionToMothership(null, e, false);

                throw new RuntimeSQLException(e);
            }
        }
    }

    private static BlockingCache<String, SchemaTableInfo> createCache(DbScope scope, boolean provisioned)
    {
        String comment = "SchemaTableInfos for " + (provisioned ? "provisioned" : "non-provisioned") + " schemas in scope " + scope.getDisplayName();

        // We modify provisioned tables inside of transactions, so use a DatabaseCache to help with proper invalidation. Issue 46951.
        return provisioned ?
            DatabaseCache.get(scope, 10000, CacheManager.UNLIMITED, comment, new SchemaTableLoader()) :
            new BlockingCache<>(CacheManager.getStringKeyCache(10000, CacheManager.UNLIMITED, comment), new SchemaTableLoader());
    }
}
