/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.exp.api.ProvisionedDbSchema;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

/**
* User: adam
* Date: 8/14/13
* Time: 5:46 AM
*/
public enum DbSchemaType
{
    // TODO: Create Uncached type?  Might make sense for non-external schema usages of Bare
    Module("module", CacheManager.YEAR, true)
    {
        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module) throws SQLException
        {
            Map<String, SchemaTableInfoFactory> metaDataTableNames = DbSchema.loadTableMetaData(scope, metaDataName);

            return module.createModuleDbSchema(scope, metaDataName, metaDataTableNames);
        }

        @Nullable
        @Override
        public org.labkey.api.module.Module getModule(String fullyQualifiedSchemaName)
        {
            Module module = ModuleLoader.getInstance().getModuleForSchemaName(fullyQualifiedSchemaName);

            // For now, throw if no module claims this schema. We could relax this to an assert (and fall back to core)
            // to provide backward compatibility for external modules that don't register their schemas.
            if (null == module)
                throw new IllegalStateException("Schema \"" + fullyQualifiedSchemaName + "\" is not claimed by any module.");

            return module;
        }
    },
    Provisioned("provisioned", CacheManager.YEAR, false)
    {
        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module)
        {
            return new ProvisionedDbSchema(metaDataName, scope);
        }
    },
    Bare("bare", CacheManager.HOUR, false)
    {
        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module) throws SQLException
        {
            Map<String, SchemaTableInfoFactory> tableInfoFactoryMap = DbSchema.loadTableMetaData(scope, metaDataName);
            return new DbSchema(metaDataName, Bare, scope, tableInfoFactoryMap, module);
        }
    },
    Fast("fast", CacheManager.SECOND*5 , false)
    {
        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module) throws SQLException
        {
            Map<String, SchemaTableInfoFactory> tableInfoFactoryMap = DbSchema.loadTableMetaData(scope, metaDataName);
            return new DbSchema(metaDataName, Fast, scope, tableInfoFactoryMap, module);
        }
    },
    All("", 0, false)
    {
        @Override
        protected long getCacheTimeToLive()
        {
            throw new IllegalStateException("Should not be caching a schema of this type");
        }

        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module)
        {
            throw new IllegalStateException("Should not be creating a schema of type " + All);
        }
    },
    // This is a marker type that tells DbScope to infer the actual DbSchemaType, for the (very rare) case when the caller doesn't know
    Unknown("", 0, false)
    {
        @Override
        protected long getCacheTimeToLive()
        {
            throw new IllegalStateException("Should not be caching a schema of this type");
        }

        @Override
        DbSchema createDbSchema(DbScope scope, String metaDataName, Module module)
        {
            throw new IllegalStateException("Should not be creating a schema of type " + Unknown);
        }
    };

    private final String _cacheKeyPostFix;  // Postfix makes it easy for All type to remove all versions of a schema from the cache
    private final long _cacheTimeToLive;
    private final boolean _applyXmlMetaData;

    private static final Collection<DbSchemaType> XML_META_DATA_TYPES;

    static
    {
        // DbSchema caching needs to know which schema types to invalidate when a schema.xml file changes... determine
        // that once, based on applyXmlMetaData setting of each type, and stash it. (At the moment, only module schemas
        // use XML metadata, but this generalization accommodates future types.)
        Collection<DbSchemaType> metaDataTypes = new LinkedList<>();

        for (DbSchemaType type : values())
            if (type.applyXmlMetaData())
                metaDataTypes.add(type);

        XML_META_DATA_TYPES = Collections.unmodifiableCollection(metaDataTypes);
    }

    DbSchemaType(String cacheKeyPostFix, long cacheTimeToLive, boolean applyXmlMetaData)
    {
        _cacheKeyPostFix = cacheKeyPostFix;
        _cacheTimeToLive = cacheTimeToLive;
        _applyXmlMetaData = applyXmlMetaData;
    }

    String getCacheKey(String schemaName)
    {
        return schemaName.toLowerCase() + "|" + _cacheKeyPostFix;
    }

    long getCacheTimeToLive()
    {
        return _cacheTimeToLive;
    }

    boolean applyXmlMetaData()
    {
        return _applyXmlMetaData;
    }

    static Collection<DbSchemaType> getXmlMetaDataTypes()
    {
        return XML_META_DATA_TYPES;
    }

    abstract DbSchema createDbSchema(DbScope scope, String metaDataName, Module module) throws SQLException;

    public @Nullable org.labkey.api.module.Module getModule(String fullyQualifiedSchemaName)
    {
        return null;
    }
}
