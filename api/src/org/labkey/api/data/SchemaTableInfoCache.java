/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.util.Pair;

/*
* User: adam
* Date: Mar 25, 2011
* Time: 5:56:47 AM
*/
public class SchemaTableInfoCache
{
    private final BlockingStringKeyCache<SchemaTableInfo> _blockingCache;

    public SchemaTableInfoCache(DbScope scope)
    {
        _blockingCache = new SchemaTableInfoBlockingCache(scope);
    }

    SchemaTableInfo get(DbSchema schema, String tableName)
    {
        String key = getCacheKey(schema, tableName);
        return _blockingCache.get(key, new Pair<>(schema, tableName));
    }

    void remove(DbSchema schema, String tableName)
    {
        String key = getCacheKey(schema, tableName);
        _blockingCache.remove(key);
    }

    void removeAllTables(String schemaName)
    {
        final String prefix = (schemaName + "/").toLowerCase();

        _blockingCache.removeUsingPrefix(prefix);
    }


    private String getCacheKey(DbSchema schema, String tableName)
    {
        return (schema.getName() + "/" + tableName).toLowerCase();
    }


    private static class SchemaTableLoader implements CacheLoader<String, SchemaTableInfo>
    {
        @Override
        public SchemaTableInfo load(String key, Object argument)
        {
            try
            {
                @SuppressWarnings({"unchecked"})
                Pair<DbSchema, String> pair = (Pair<DbSchema, String>)argument;
                DbSchema schema = pair.first;
                String tableName = pair.second;

                return schema.loadTable(tableName);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Changed from "return null" to "throw runtimeexception" so admin is made aware of the cause of the problem
            }
        }
    }


    private static class SchemaTableInfoBlockingCache extends BlockingStringKeyCache<SchemaTableInfo>
    {
        private SchemaTableInfoBlockingCache(DbScope scope)
        {
            super(createCache(scope), new SchemaTableLoader());

            CacheTimeChooser<String> cacheTimeChooser = scope.getTableCacheTimeChooser();

            if (null != cacheTimeChooser)
                setCacheTimeChooser(cacheTimeChooser);
        }
    }

    private static StringKeyCache<Wrapper<SchemaTableInfo>> createCache(DbScope scope)
    {
        return CacheManager.getStringKeyCache(10000, scope.getCacheDefaultTimeToLive(), "SchemaTableInfos for " + scope.getDisplayName());
    }
}
