package org.labkey.di.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 9/13/13
 * Time: 4:26 PM
 */
public class DescriptorCache
{
    private static final String ETL_MODULES_KEY = "~~etl_modules~~";
    private static final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, "ETLs and ETL Collections", null);

    @NotNull
    static Collection<ScheduledPipelineJobDescriptor> getDescriptors(Container c)
    {
        Set<Module> activeModules = c.getActiveModules();
        List<Module> etlModules = getModulesWithEtls();
        ArrayList<ScheduledPipelineJobDescriptor> descriptors = new ArrayList<>();

        for (Module etlModule : etlModules)
        {
            if (activeModules.contains(etlModule))
            {
                List<String> configNames = getConfigFilenamesForModule(etlModule);

                for (String configName : configNames)
                {
                    ScheduledPipelineJobDescriptor descriptor = getDescriptor(TransformManager.get().createConfigId(etlModule.getName(), configName));

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


    @NotNull
    private static List<Module> getModulesWithEtls()
    {
        //noinspection unchecked
        return (List<Module>)BLOCKING_CACHE.get(ETL_MODULES_KEY, null, ALL_ETL_MODULES_LOADER);
    }


    // Will return empty list if etls directory gets deleted, no ETLS, etc.
    @NotNull
    private static List<String> getConfigFilenamesForModule(Module module)
    {
        //noinspection unchecked
        return (List<String>)BLOCKING_CACHE.get(module.getName(), null, CONFIG_FILENAMES_FOR_MODULE_LOADER);
    }


    private static final CacheLoader DESCRIPTOR_LOADER = new CacheLoader<String, ScheduledPipelineJobDescriptor>()
    {
        @Override
        public ScheduledPipelineJobDescriptor load(String configId, @Nullable Object argument)
        {
            Pair<Module, String> pair = TransformManager.get().parseConfigId(configId);
            Module module = pair.first;
            String configFile = pair.second;

            Path configPath = new Path("etls", configFile + ".xml");
            Resource config = pair.first.getModuleResolver().lookup(configPath);

            if (config != null && config.isFile())
                return TransformManager.get().parseETL(config, module.getName());
            else
                return null;
        }
    };


    private static final CacheLoader ALL_ETL_MODULES_LOADER = new CacheLoader<String, List<Module>>()
    {
        @Override
        public List<Module> load(String key, @Nullable Object argument)
        {
            List<Module> modules = new LinkedList<>();
            Path etlsDirPath = new Path("etls");

            for (Module module : ModuleLoader.getInstance().getModules())
            {
                Resource etlsDir = module.getModuleResolver().lookup(etlsDirPath);
                if (null != etlsDir && etlsDir.isCollection())
                    modules.add(module);
            }

            return modules;
        }
    };

    private static final CacheLoader CONFIG_FILENAMES_FOR_MODULE_LOADER = new CacheLoader<String, List<String>>()
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
                // Create a list of all files that end in ".xml" in this directory. Store just the base name,
                // which matches the descriptor ID format.
                for (Resource r : etlsDir.list())
                {
                    if (r.isFile())
                    {
                        String name = r.getName();

                        if (r.getName().endsWith(".xml"))
                            configs.add(name.substring(0, name.length() - 4));
                    }
                }
            }

            return configs;
        }
    };
}
