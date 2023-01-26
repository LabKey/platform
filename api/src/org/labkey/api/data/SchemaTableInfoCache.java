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
import org.labkey.api.cache.Wrapper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.logging.LogHelper;

/*
* User: adam
* Date: Mar 25, 2011
* Time: 5:56:47 AM
*/
public class SchemaTableInfoCache
{
    private static final Logger LOG = LogHelper.getLogger(SchemaTableInfoCache.class, "Loading of schema and table metadata from database schemas");

    private final BlockingCache<String, SchemaTableInfo> _blockingCache;
    private final DbSchemaType _schemaType;

    public SchemaTableInfoCache(DbScope scope, DbSchemaType schemaType)
    {
        _blockingCache = new SchemaTableInfoBlockingCache(scope, schemaType);
        _schemaType = schemaType;
    }

    <OptionType extends DbScope.SchemaTableOptions> SchemaTableInfo get(@NotNull OptionType options)
    {
        String key = getCacheKey(options.getSchema().getName(), options.getTableName());
        return _blockingCache.get(key, options);
    }

    void remove(@NotNull String schemaName, @NotNull String tableName)
    {
        LOG.debug("remove " + _schemaType + " schema table: " + schemaName + "." + tableName);
        String key = getCacheKey(schemaName, tableName);
        _blockingCache.remove(key);
    }

    void removeAllTables(@NotNull String schemaName)
    {
        LOG.debug("remove all " + _schemaType + " schema tables: " + schemaName);
        final String prefix = _schemaType.getCacheKey(schemaName);

        _blockingCache.removeUsingFilter(new Cache.StringPrefixFilter(prefix));
    }

    private String getCacheKey(@NotNull String schemaName, @NotNull String tableName)
    {
        return _schemaType.getCacheKey(schemaName) + "|" + tableName.toLowerCase();
    }

    private static class SchemaTableLoader implements CacheLoader<String, SchemaTableInfo>
    {
        @Override
        public SchemaTableInfo load(@NotNull String key, Object argument)
        {
            try
            {
                DbScope.SchemaTableOptions options = (DbScope.SchemaTableOptions)argument;

                LOG.debug("loading schema table: " + options.getSchema().getName() + "." + options.getTableName());
                return options.getSchema().loadTable(options.getTableName(), options);
            }
            catch (Throwable t)
            {
                // Log all problems to mothership so admin and LabKey are made aware of the cause of the problem, but return
                // null so other tables in this schema can load. One previous example: MV indicators on list columns with
                // very long names used to be a problem, but that was fixed. There may be other scenarios that throw.
                ExceptionUtil.logExceptionToMothership(null, t);

                return null;
            }
        }
    }

    private static class SchemaTableInfoBlockingCache extends BlockingCache<String, SchemaTableInfo>
    {
        private SchemaTableInfoBlockingCache(DbScope scope, DbSchemaType schemaType)
        {
            super(createCache(scope, schemaType), new SchemaTableLoader());
        }
    }

    private static Cache<String, Wrapper<SchemaTableInfo>> createCache(DbScope scope, DbSchemaType schemaType)
    {
        String comment = "SchemaTableInfos for " + schemaType + " schemas in scope \"" + scope.getDisplayName() + "\"";

        // We modify provisioned tables inside of transactions, so use a DatabaseCache to help with proper invalidation. See #46951.
        if (schemaType == DbSchemaType.Provisioned)
            return new DatabaseCache<>(scope, 10000, schemaType.getCacheTimeToLive(), comment);
        else
            return CacheManager.getStringKeyCache(10000, schemaType.getCacheTimeToLive(), comment);
    }
}
