/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import org.labkey.api.cache.Wrapper;
import org.labkey.api.settings.AppProps;

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

    public DbSchemaCache(DbScope scope)
    {
        _scope = scope;
        _cache = new DbSchemaBlockingCache(_scope.getDisplayName(), _scope.getCacheDefaultTimeToLive());

        CacheTimeChooser<String> cacheTimeChooser = scope.getSchemaCacheTimeChooser();

        if (null != cacheTimeChooser)
            _cache.setCacheTimeChooser(cacheTimeChooser);
    }

    @NotNull DbSchema get(String schemaName)
    {
        return _cache.get(schemaName.toLowerCase(), schemaName);
    }

    void remove(String schemaName)
    {
        _cache.remove(schemaName.toLowerCase());
    }


    private class DbSchemaLoader implements CacheLoader<String, DbSchema>
    {
        @Override
        public DbSchema load(String key, Object requestedSchemaName)
        {
            try
            {
                return _scope.loadSchema((String)requestedSchemaName);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Make admin aware of the cause of the problem
            }
        }
    }


    private class DbSchemaBlockingCache extends BlockingStringKeyCache<DbSchema>
    {
        public DbSchemaBlockingCache(String name, long defaultTimeToLive)
        {
            super(CacheManager.<String, Wrapper<DbSchema>>getCache(10000, defaultTimeToLive, "DbSchemas for " + name), new DbSchemaLoader());
        }

        @Override
        protected boolean isValid(Wrapper<DbSchema> w, String key, Object argument, CacheLoader loader)
        {
            boolean isValid = super.isValid(w, key, argument, loader);

            if (isValid)
            {
                DbSchema schema = w.getValue();

                if (AppProps.getInstance().isDevMode() &&
                    // TODO: Remove isLabKeyScope() hack that works around DbSchema.isStale() assert
                    schema.getScope().isLabKeyScope() && schema.isStale())
                {
                    isValid = false;
                }
            }

            return isValid;
        }
    }
}
