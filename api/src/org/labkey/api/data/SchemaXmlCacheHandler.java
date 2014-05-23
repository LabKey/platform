/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 5/10/2014
 * Time: 1:18 PM
 */
public class SchemaXmlCacheHandler implements ModuleResourceCacheHandler<String, TablesDocument>
{
    static final String DIR_NAME = "schemas";

    @Override
    public boolean isResourceFile(String filename)
    {
        return filename.endsWith(".xml");
    }

    @Override
    public String getResourceName(Module module, String filename)
    {
        return filename;
    }

    @Override
    public String createCacheKey(Module module, String resourceName)
    {
        return ModuleResourceCache.createCacheKey(module, resourceName);
    }

    @Override
    public CacheLoader<String, TablesDocument> getResourceLoader()
    {
        return new CacheLoader<String, TablesDocument>()
        {
            @Override
            public TablesDocument load(String key, @Nullable Object argument)
            {
                ModuleResourceCache.CacheId id = ModuleResourceCache.parseCacheKey(key);
                Module module = id.getModule();
                String filename = id.getName();
                Path path = new Path(DIR_NAME, filename);
                Resource resource  = module.getModuleResolver().lookup(path);

                assert null != resource : "Expected schema metadata xml file";

                try (InputStream xmlStream = resource.getInputStream())
                {
                    if (null != xmlStream)
                    {
                        return TablesDocument.Factory.parse(xmlStream);
                    }
                }
                catch (IOException | XmlException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }

                return null;
            }
        };
    }

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(final Module module)
    {
        return CHAINED_LISTENER;
    }


    // No need to distinguish by module, since DbSchemas are a single global list
    private static final FileSystemDirectoryListener CHAINED_LISTENER = new FileSystemDirectoryListener()
    {
        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(entry);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(entry);
        }

        @Override
        public void overflow()
        {
        }

        private void uncacheDbSchema(java.nio.file.Path entry)
        {
            String filename = entry.toString();
            String fullyQualified = FileUtil.getBaseName(filename);

            // Special case "labkey" schema, which gets added to all module data sources
            if ("labkey".equalsIgnoreCase(fullyQualified))
            {
                // Invalidate "labkey" in every scope if its meta data changes
                for (DbScope scope : DbScope.getDbScopes())
                    invalidateSchema(scope, "labkey");
            }
            else
            {
                Pair<DbScope, String> pair = DbSchema.getDbScopeAndSchemaName(fullyQualified);
                invalidateSchema(pair.getKey(), pair.getValue());
            }
        }

        private void invalidateSchema(DbScope scope, String schemaName)
        {
            // Invalidate all schemas with a type that uses XML metadata
            for (DbSchemaType type : DbSchemaType.getXmlMetaDataTypes())
                scope.invalidateSchema(schemaName, type);
        }
    };
}
