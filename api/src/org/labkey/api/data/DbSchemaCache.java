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
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Filter;

/*
* User: adam
* Date: Mar 20, 2011
* Time: 2:53:51 PM
*/

// Every scope has its own cache of DbSchemas
public class DbSchemaCache
{
    private final DbScope _scope;
    private final BlockingStringKeyCache<DbSchema> _blockingCache = new DbSchemaBlockingCache();
    private final IncompleteSchemaFilter _incompleteFilter = new IncompleteSchemaFilter();

    public DbSchemaCache(DbScope scope)
    {
        _scope = scope;
    }

    @NotNull DbSchema get(String schemaName)
    {
        return _blockingCache.get(schemaName);
    }

    void remove(String schemaName)
    {
        _blockingCache.remove(schemaName);
    }

    void removeIncomplete()
    {
        _blockingCache.removeUsingFilter(_incompleteFilter);
    }

    
    private class IncompleteSchemaFilter implements Filter<String>
    {
        @Override
        public boolean accept(String schemaName)
        {
            Module module = ModuleLoader.getInstance().getModuleForSchemaName(schemaName);

            // We only care about schemas associated with a module (not external schemas)
            if (null != module)
            {
                ModuleContext context = ModuleLoader.getInstance().getModuleContext(module);

                if (!context.isInstallComplete())
                {
                    _scope.invalidateAllTables(schemaName);
                    return true;
                }
            }

            return false;
        }
    }


    private class DbSchemaLoader implements CacheLoader<String, DbSchema>
    {
        @Override
        public DbSchema load(String schemaName, Object argument)
        {
            try
            {
                return _scope.loadSchema(schemaName);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Make admin aware of the cause of the problem
            }
        }
    }


    private class DbSchemaBlockingCache extends BlockingStringKeyCache<DbSchema>
    {
        public DbSchemaBlockingCache()
        {
            // Add scope name?
            super(CacheManager.getStringKeyCache(10000, CacheManager.YEAR, "DbSchemas"), new DbSchemaLoader());
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
