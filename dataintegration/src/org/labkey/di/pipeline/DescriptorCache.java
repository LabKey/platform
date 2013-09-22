package org.labkey.di.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 9/13/13
 * Time: 4:26 PM
 */
public class DescriptorCache
{
    // List of modules that expose ETL configurations. Since the number of modules exposing etls is likely small, this
    // is a nice optimization. More importantly, this is where we hook our file system listener, so we can watch for
    // create, delete, and update events. The module list is initialized at startup time and never changes.
    private static final List<Module> _etlModules = new CopyOnWriteArrayList<>();
    private static final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, "ETL job descriptors and collections", null);
    private static final FileSystemWatcher WATCHER = FileSystemWatchers.get("ETL descriptor cache watcher");

    // At startup, we record all modules with "etls" directories and register a file listener to monitor for changes.
    // Loading the list of configurations in each module and the descriptors themselves happens lazily.
    static void registerModule(Module module)
    {
        Path etlsDirPath = new Path("etls");
        Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);

        if (null != etlsDir && etlsDir.isCollection())
        {
            _etlModules.add(module);

            try
            {
                // TODO: Dev mode only?
                // TODO: Integrate this better with Resource
                ((MergedDirectoryResource)etlsDir).registerListener(WATCHER, new EtlDirectoryListener(module),
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    @NotNull
    static Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        Set<Module> activeModules = c.getActiveModules();
        ArrayList<ScheduledPipelineJobDescriptor> descriptors = new ArrayList<>();

        for (Module etlModule : _etlModules)
        {
            if (activeModules.contains(etlModule))
            {
                List<String> configNames = getConfigNames(etlModule);

                for (String configName : configNames)
                {
                    ScheduledPipelineJobDescriptor descriptor = getDescriptor(TransformManager.get().createConfigId(etlModule, configName));

                    if (null != descriptor)
                        descriptors.add(descriptor);
                }
            }
        }

        return descriptors;
    }


    @Nullable
    static ScheduledPipelineJobDescriptor getDescriptor(String configId)
    {
        //noinspection unchecked
        return (ScheduledPipelineJobDescriptor)BLOCKING_CACHE.get(configId, null, DESCRIPTOR_LOADER);
    }


    // Clear a single descriptor from the cache
    static void removeDescriptor(Module module, String configName)
    {
        String id = TransformManager.get().createConfigId(module, configName);
        BLOCKING_CACHE.remove(id);
    }


    // Clear a single module's list of ETL configurations from the cache (but leave the descriptors cached)
    static void removeConfigNames(Module module)
    {
        BLOCKING_CACHE.remove(module.getName());
    }


    // Clear the whole cache
    static void clear()
    {
        BLOCKING_CACHE.clear();
    }


    // Will return empty list if etls directory gets deleted, no ETLS, etc.
    @NotNull
    private static List<String> getConfigNames(Module module)
    {
        //noinspection unchecked
        return (List<String>)BLOCKING_CACHE.get(module.getName(), null, CONFIG_NAMES_LOADER);
    }


    private static final CacheLoader DESCRIPTOR_LOADER = new CacheLoader<String, ScheduledPipelineJobDescriptor>()
    {
        @Override
        public ScheduledPipelineJobDescriptor load(String configId, @Nullable Object argument)
        {
            Pair<Module, String> pair = TransformManager.get().parseConfigId(configId);
            Module module = pair.first;
            String configName = pair.second;

            Path configPath = new Path("etls", configName + ".xml");
            Resource config = pair.first.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return TransformManager.get().parseETL(config, module);
            else
                return null;
        }
    };


    private static final CacheLoader CONFIG_NAMES_LOADER = new CacheLoader<String, List<String>>()
    {
        @Override
        public List<String> load(String moduleName, @Nullable Object argument)
        {
            Path etlsDirPath = new Path("etls");
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

                        if (TransformManager.get().isConfigFile(name))
                            configs.add(FileUtil.getBaseName(name));
                    }
                }
            }

            return configs;
        }
    };
}
