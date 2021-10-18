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

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCacheListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.data.xml.TablesDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages caching for module XML-based schema-scoped metadata.
 * User: adam
 * Date: 5/10/2014
 */
public class SchemaXmlCacheHandler implements ModuleResourceCacheHandler<Map<String, TablesDocument>>
{
    @Override
    public Map<String, TablesDocument> load(Stream<? extends Resource> resources, Module module)
    {
        Map<String, TablesDocument> map = new CaseInsensitiveHashMap<>();

        resources
            .filter(resource -> isSchemaXmlFile(resource.getName()))
            .forEach(resource -> {
                TablesDocument doc = getTablesDoc(resource);
                if (null != doc)
                    map.put(resource.getName(), doc);
            });

        return unmodifiable(map);
    }

    // Return an unmodifiable list of schema.xml filenames in this module, bypassing the cache and file listeners. Handy
    // at startup, so we don't register listeners and leak modules before pruning occurs.
    public static List<String> getFilenames(Module module)
    {
        Resource schemas = module.getModuleResource(QueryService.MODULE_SCHEMAS_PATH);

        return null == schemas ? Collections.emptyList() : schemas.list().stream()
            .map(Resource::getName)
            .filter(SchemaXmlCacheHandler::isSchemaXmlFile)
            .collect(Collectors.toUnmodifiableList());
    }

    private static boolean isSchemaXmlFile(String filename)
    {
        return filename.endsWith(".xml") && !filename.endsWith(QueryService.SCHEMA_TEMPLATE_EXTENSION);
    }

    private @Nullable TablesDocument getTablesDoc(Resource resource)
    {
        TablesDocument doc = null;

        try (InputStream xmlStream = resource.getInputStream())
        {
            if (null != xmlStream)
            {
                doc = TablesDocument.Factory.parse(xmlStream);
            }
        }
        catch (IOException | XmlException e)
        {
            Exception wrap = new Exception("Exception while attempting to load " + resource.getPath(), e);
            ExceptionUtil.logExceptionToMothership(null, wrap);
        }

        return doc;
    }

    @Nullable
    @Override
    public ModuleResourceCacheListener createChainedListener(final Module module)
    {
        return CHAINED_LISTENER;
    }


    // No need to distinguish by module, since DbSchemas are a single global list
    private static final ModuleResourceCacheListener CHAINED_LISTENER = new ModuleResourceCacheListener()
    {
        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(directory, entry);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(directory, entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            uncacheDbSchema(directory, entry);
        }

        @Override
        public void overflow()
        {
        }

        @Override
        public void moduleChanged(Module module)
        {
            module.getSchemaNames().stream()
                .map(DbSchema::getDbScopeAndSchemaName)
                .forEach(pair->invalidateSchema(pair.getKey(), pair.getValue()));
        }

        private void uncacheDbSchema(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            // Makes sure we're in /resources/schemas, not /resources. For example, module.xml lives in /resources and
            // it's definitely not a schema xml file.
            if (directory.endsWith(QueryService.MODULE_SCHEMAS_DIRECTORY))
            {
                String filename = entry.toString();

                if (isSchemaXmlFile(filename))
                {
                    String fullyQualified = FileUtil.getBaseName(filename);

                    // Special case "labkey" schema, which gets added to all module data sources
                    if ("labkey".equalsIgnoreCase(fullyQualified))
                    {
                        // Invalidate "labkey" in every scope if its metadata changes
                        for (DbScope scope : DbScope.getDbScopes())
                            invalidateSchema(scope, "labkey");
                    }
                    else
                    {
                        Pair<DbScope, String> pair = DbSchema.getDbScopeAndSchemaName(fullyQualified);
                        invalidateSchema(pair.getKey(), pair.getValue());
                    }
                }
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
