/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.query.olap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.util.regex.Pattern;


public class OlapSchemaCacheHandler implements ModuleResourceCacheHandler<String, OlapSchemaDescriptor>
{
    public static final String DIR_NAME = "olap";

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return new OlapDirectoryListener();
    }

    @Override
    public boolean isResourceFile(String filename)
    {
        return filename.endsWith(".xml");
    }

    @Override
    public String getResourceName(Module module, String filename)
    {
        assert isResourceFile(filename) : "Configuration filename \"" + filename + "\" does not end with .xml";
        return FileUtil.getBaseName(filename);
    }

    @Override
    public String createCacheKey(Module module, String name)
    {
        return createOlapCacheKey(module, name);
    }

    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("("+ ModuleLoader.MODULE_NAME_REGEX + "):/(.+)");

    @Override
    public CacheLoader<String, OlapSchemaDescriptor> getResourceLoader()
    {
        return DESCRIPTOR_LOADER;
    }

    private final CacheLoader<String, OlapSchemaDescriptor> DESCRIPTOR_LOADER = new CacheLoader<String, OlapSchemaDescriptor>()
    {
        @Override
        public OlapSchemaDescriptor load(String configId, @Nullable Object argument)
        {
            ModuleResourceCache.CacheId tid = parseOlapCacheKey(configId);
            Module module = tid.getModule();
            String configName = tid.getName();
            Path configPath = new Path(DIR_NAME, configName + ".xml");
            Resource config  = module.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return new ModuleOlapSchemaDescriptor(configId, module, config);
            else
                return null;
        }
    };

    public static String createOlapCacheKey(Module module, String name)
    {
        return module.getName() + ":/" + name;
    }

    public static ModuleResourceCache.CacheId parseOlapCacheKey(String schemaId)
    {
        ModuleResourceCache.CacheId tid = ModuleResourceCache.parseCacheKey(schemaId, CONFIG_ID_PATTERN);
        return tid;
    }


    private class OlapDirectoryListener implements FileSystemDirectoryListener
    {
        private OlapDirectoryListener()
        {
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void overflow()
        {
        }
    }
}
