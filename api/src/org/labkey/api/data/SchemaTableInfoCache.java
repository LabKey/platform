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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.util.ExceptionUtil;

/*
* User: adam
* Date: Mar 25, 2011
* Time: 5:56:47 AM
*/
public class SchemaTableInfoCache
{
    private static final Logger LOG = Logger.getLogger(SchemaTableInfoCache.class);

    private final BlockingCache<String, SchemaTableInfo> _blockingCache;

    public SchemaTableInfoCache(DbScope scope)
    {
        _blockingCache = new SchemaTableInfoBlockingCache(scope);
    }

    <OptionType extends DbScope.SchemaTableOptions> SchemaTableInfo get(@NotNull OptionType options)
    {
        String key = getCacheKey(options.getSchema(), options.getTableName());
        return _blockingCache.get(key, options);
    }

    void remove(@NotNull DbSchema schema, @NotNull String tableName)
    {
        if (schema.getType() == DbSchemaType.Module)
            LOG.warn("removing module schema table: " + schema.getName() + "." + tableName, new Throwable("removing module schema table: " + schema.getName() + "." + tableName));
        else
            LOG.debug("remove " + schema.getType() + " schema table: " + schema.getName() + "." + tableName);
        String key = getCacheKey(schema, tableName);
        _blockingCache.remove(key);
    }

    void remove(@NotNull String schemaName, @NotNull String tableName, @NotNull DbSchemaType type)
    {
        if (type == DbSchemaType.Module)
            LOG.warn("removing module schema table: " + schemaName + "." + tableName, new Throwable("removing module schema table: " + schemaName + "." + tableName));
        else
            LOG.debug("remove " + type + " schema table: " + schemaName + "." + tableName);
        String key = getCacheKey(schemaName, tableName, type);
        _blockingCache.remove(key);
    }

    void removeAllTables(@NotNull String schemaName, DbSchemaType type)
    {
        if (type == DbSchemaType.Module)
            LOG.warn("removing all module schema tables: " + schemaName, new Throwable("removing all module schema tables: " + schemaName));
        else
            LOG.debug("remove all " + type + " schema tables: " + schemaName);
        final String prefix = type.getCacheKey(schemaName);

        _blockingCache.removeUsingFilter(new Cache.StringPrefixFilter(prefix));
    }


    private String getCacheKey(@NotNull DbSchema schema, @NotNull String tableName)
    {
        return getCacheKey(schema.getName(), tableName, schema.getType());
    }

    private String getCacheKey(@NotNull String schemaName, @NotNull String tableName, @NotNull DbSchemaType type)
    {
        return type.getCacheKey(schemaName) + "|" + tableName.toLowerCase();
    }

    private static class SchemaTableLoader implements CacheLoader<String, SchemaTableInfo>
    {
        @Override
        public SchemaTableInfo load(String key, Object argument)
        {
            try
            {
                @SuppressWarnings({"unchecked"})
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


    // Ask the DbSchemaType how long to cache each table
    private static final CacheTimeChooser<String> TABLE_CACHE_TIME_CHOOSER = (key, argument) -> {
        @SuppressWarnings({"unchecked"})
        DbScope.SchemaTableOptions options = (DbScope.SchemaTableOptions)argument;

        return options.getSchema().getType().getCacheTimeToLive();
    };

    private static class SchemaTableInfoBlockingCache extends BlockingCache<String, SchemaTableInfo>
    {
        private SchemaTableInfoBlockingCache(DbScope scope)
        {
            super(createCache(scope), new SchemaTableLoader());
            setCacheTimeChooser(TABLE_CACHE_TIME_CHOOSER);
        }
    }


    private static Cache<String, Wrapper<SchemaTableInfo>> createCache(DbScope scope)
    {
        return CacheManager.getStringKeyCache(10000, CacheManager.UNLIMITED, "SchemaTableInfos for " + scope.getDisplayName());
    }
}
