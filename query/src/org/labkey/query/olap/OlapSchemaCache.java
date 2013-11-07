package org.labkey.query.olap;

/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class OlapSchemaCache
{
    private static final String OLAP_DIR = "olap";
    private final List<Module> _olapModules = new CopyOnWriteArrayList<>();
    private final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, "Olap cube defintions", null);
    private final FileSystemWatcher WATCHER = FileSystemWatchers.get("Cube descriptor cache watcher");

    static OlapSchemaCache _instance = new OlapSchemaCache();

    public static OlapSchemaCache get()
    {
        return _instance;
    }

    private OlapSchemaCache()
    {}


    // At startup, we record all modules with "olap" directories and register a file listener to monitor for changes.
    // Loading the list of configurations in each module and the descriptors themselves happens lazily.
    public void registerModule(Module module)
    {
        Path olapDirPath = new Path(OLAP_DIR);
        Resource olapDir = module.getModuleResolver().lookup(olapDirPath);

        if (null != olapDir && olapDir.isCollection())
        {
            _olapModules.add(module);

            // TODO: Integrate this better with Resource
            ((MergedDirectoryResource)olapDir).registerListener(WATCHER, new OlapDirectoryListener(module),
                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }
    }

    static boolean isConfigFile(String filename)
    {
        return filename.endsWith(".xml");
    }


    String getConfigName(String filename)
    {
        assert filename.endsWith(".xml") : "Configuration filename \"" + filename + "\" does not end with .xml";
        return FileUtil.getBaseName(filename);
    }


    String createConfigId(Module module, String name)
    {
        return module.getName() + ":/" + name;
    }


    private static final Pattern CONFIG_ID_PATTERN = Pattern.compile("(\\w+):/(.+)");


    Pair<Module, String> parseConfigId(String configId)
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


    @NotNull
    public Collection<OlapSchemaDescriptor> getDescriptors(Container c)
    {
        Set<Module> activeModules = c.getActiveModules();
        ArrayList<OlapSchemaDescriptor> descriptors = new ArrayList<>();

        for (Module etlModule : _olapModules)
        {
            if (activeModules.contains(etlModule))
            {
                List<String> configNames = getConfigNames(etlModule);

                for (String configName : configNames)
                {
                    OlapSchemaDescriptor descriptor = getDescriptor(c, createConfigId(etlModule, configName));

                    if (null != descriptor)
                        descriptors.add(descriptor);
                }
            }
        }

        return descriptors;
    }


    @Nullable
    public OlapSchemaDescriptor getDescriptor(@NotNull Container c, @NotNull String schemaId)
    {
        OlapSchemaDescriptor d = getDescriptor(schemaId);
        if (null != d && c.getActiveModules().contains(d.getModule()))
            return d;
        return null;
    }

    @Nullable
    public OlapSchemaDescriptor getDescriptor(String schemaId)
    {
        //noinspection unchecked
        return (OlapSchemaDescriptor)BLOCKING_CACHE.get(schemaId, null, DESCRIPTOR_LOADER);
    }


    // Clear a single descriptor from the cache
    void removeDescriptor(Module module, String configName)
    {
        String id = createConfigId(module, configName);
        BLOCKING_CACHE.remove(id);
    }


    // Clear a single module's list of ETL configurations from the cache (but leave the descriptors cached)
    void removeConfigNames(Module module)
    {
        BLOCKING_CACHE.remove(module.getName());
    }


    // Clear the whole cache
    void clear()
    {
        BLOCKING_CACHE.clear();
    }


    // Will return empty list if etls directory gets deleted, no ETLS, etc.
    @NotNull
    private List<String> getConfigNames(Module module)
    {
        //noinspection unchecked
        return (List<String>)BLOCKING_CACHE.get(module.getName(), null, CONFIG_NAMES_LOADER);
    }


    private final CacheLoader DESCRIPTOR_LOADER = new CacheLoader<String, OlapSchemaDescriptor>()
    {
        @Override
        public OlapSchemaDescriptor load(String configId, @Nullable Object argument)
        {
            Pair<Module, String> pair = parseConfigId(configId);
            Module module = pair.first;
            String configName = pair.second;

            Path configPath = new Path(OLAP_DIR, configName + ".xml");
            Resource config = pair.first.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return new OlapSchemaDescriptor(configId, module, config);
            else
                return null;
        }
    };


    private static final CacheLoader CONFIG_NAMES_LOADER = new CacheLoader<String, List<String>>()
    {
        @Override
        public List<String> load(String moduleName, @Nullable Object argument)
        {
            Path etlsDirPath = new Path(OLAP_DIR);
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
            List<String> configs = new LinkedList<>();

            if (etlsDir != null && etlsDir.isCollection())
            {
                // Create a list of all files in this directory that conform to the configuration file format (end in ".xml").
                // Store just the base name, which matches the descriptor ID format.
                for (Resource r : etlsDir.list())
                {
                    if (r.isFile())
                    {
                        String name = r.getName();

                        if (isConfigFile(name))
                            configs.add(FileUtil.getBaseName(name));
                    }
                }
            }

            return Collections.unmodifiableList(configs);
        }
    };



    private class OlapDirectoryListener implements FileSystemDirectoryListener
    {
        final Module _module;

        OlapDirectoryListener(Module module)
        {
            _module = module;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeConfigNames(_module);
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeConfigNames(_module);
            removeDescriptor(entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeDescriptor(entry);
            ServerManager.olapSchemaDescriptorChanged(null);
        }

        @Override
        public void overflow()
        {
        }

        private void removeDescriptor(java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (isConfigFile(filename))
                OlapSchemaCache.this.removeDescriptor(_module, getConfigName(filename));
        }
    }
}
