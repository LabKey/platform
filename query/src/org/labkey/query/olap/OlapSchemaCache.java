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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class OlapSchemaCache extends ModuleResourceCache<OlapSchemaDescriptor>
{
    private static final OlapSchemaCache _instance = new OlapSchemaCache();

    public static OlapSchemaCache get()
    {
        return _instance;
    }

    private OlapSchemaCache()
    {
        super(new Path("olap"), "Olap cube defintions");
    }

    @Nullable
    @Override
    protected FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return new OlapDirectoryListener();
    }

    protected boolean isResourceFile(String filename)
    {
        return filename.endsWith(".xml");
    }

    protected String getResourceName(Module module, String filename)
    {
        assert isResourceFile(filename) : "Configuration filename \"" + filename + "\" does not end with .xml";
        return FileUtil.getBaseName(filename);
    }

    protected String createCacheKey(Module module, String name)
    {
        return module.getName() + ":/" + name;
    }

    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("(\\w+):/(.+)");

    private Pair<Module, String> parseConfigId(String configId)
    {
        // Parse out the module name and the config name
        Matcher matcher = CONFIG_ID_PATTERN.matcher(configId);

        if (!matcher.matches() || matcher.groupCount() != 2)
            throw new IllegalStateException("Unrecognized configuration ID format: " + configId);

        String moduleName = matcher.group(1);
        String filename = matcher.group(2);
        Module module = ModuleLoader.getInstance().getModule(moduleName);

        if (null == module)
            throw new IllegalStateException("Module does not exist: " + moduleName);

        return new Pair<>(module, filename);
    }

    @Nullable   // TODO: Doesn't really belong in the cache implementation...
    public OlapSchemaDescriptor getDescriptor(@NotNull Container c, @NotNull String schemaId)
    {
        OlapSchemaDescriptor d = getResource(schemaId);
        if (null != d && c.getActiveModules().contains(d.getModule()))
            return d;
        return null;
    }

    @Override
    protected CacheLoader<String, OlapSchemaDescriptor> getResourceLoader()
    {
        return DESCRIPTOR_LOADER;
    }

    private final CacheLoader<String, OlapSchemaDescriptor> DESCRIPTOR_LOADER = new CacheLoader<String, OlapSchemaDescriptor>()
    {
        @Override
        public OlapSchemaDescriptor load(String configId, @Nullable Object argument)
        {
            Pair<Module, String> pair = parseConfigId(configId);
            Module module = pair.first;
            String configName = pair.second;

            Path configPath = _dirName.append(configName + ".xml");
            Resource config = pair.first.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return new OlapSchemaDescriptor(configId, module, config);
            else
                return null;
        }
    };

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
