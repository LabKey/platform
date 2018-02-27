/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.module.ModuleLoader;

/*
* User: adam
* Date: Mar 20, 2011
* Time: 2:53:51 PM
*/

// Every scope has its own cache of DbSchemas
public class DbSchemaCache
{
    private final DbScope _scope;
    private final BlockingStringKeyCache<DbSchema> _cache;

    // Ask the DbSchemaType how long to cache each schema
    private final CacheTimeChooser<String> SCHEMA_CACHE_TIME_CHOOSER = (key, argument) -> {
        @SuppressWarnings({"unchecked"})
        SchemaDetails details = (SchemaDetails)argument;
        return details.getType().getCacheTimeToLive();
    };

    public DbSchemaCache(DbScope scope)
    {
        _scope = scope;
        _cache = new DbSchemaBlockingCache(_scope.getDisplayName());
    }

    @NotNull DbSchema get(String schemaName, DbSchemaType type)
    {
        // Infer type if it's unknown... should be rare
        if (DbSchemaType.Unknown == type)
        {
            String qualifiedName = DbSchema.getDisplayName(_scope, schemaName);
            type = ModuleLoader.getInstance().getSchemaTypeForSchemaName(qualifiedName);

            if (null == type)
                type = DbSchemaType.Bare;  // Schema isn't claimed by a module
        }

        return _cache.get(getKey(schemaName, type), new SchemaDetails(schemaName, type));
    }

    void remove(String schemaName, DbSchemaType type)
    {
        _cache.removeUsingPrefix(getKey(schemaName, type));
    }

    private String getKey(String schemaName, DbSchemaType type)
    {
        return type.getCacheKey(schemaName);
    }


    private class SchemaDetails
    {
        private final String _schemaName;
        private final DbSchemaType _type;

        private SchemaDetails(String requestedSchemaName, DbSchemaType type)
        {
            _schemaName = requestedSchemaName;
            _type = type;
        }

        private String getRequestedSchemaName()
        {
            return _schemaName;
        }

        public DbSchemaType getType()
        {
            return _type;
        }
    }


    private class DbSchemaLoader implements CacheLoader<String, DbSchema>
    {
        @Override
        public DbSchema load(String key, Object schemaDetails)
        {
            try
            {
                SchemaDetails details = (SchemaDetails)schemaDetails;
                return _scope.loadSchema(details.getRequestedSchemaName(), details.getType());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Make admin aware of the cause of the problem
            }
        }
    }


    private class DbSchemaBlockingCache extends BlockingStringKeyCache<DbSchema>
    {
        public DbSchemaBlockingCache(String dsName)
        {
            super(CacheManager.getCache(1000, CacheManager.UNLIMITED, "DbSchemas for " + dsName), new DbSchemaLoader());
            setCacheTimeChooser(SCHEMA_CACHE_TIME_CHOOSER);
        }
    }
}
