package org.labkey.assay;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleAssayCache
{
    private static final ModuleAssayCache INSTANCE = new ModuleAssayCache();
    private static final Logger LOG = LogManager.getLogger(ModuleAssayCache.class);
    private static final ModuleResourceCache<Collection<ModuleAssayProvider>> PROVIDER_CACHE = ModuleResourceCaches.create("Module assay providers", new ModuleAssayCacheHandler(), ResourceRootProvider.getAssayProviders(Path.rootPath));
    private static final Object PROVIDER_LOCK = new Object();

    static ModuleAssayCache get()
    {
        return INSTANCE;
    }

    private ModuleAssayCache()
    {
    }

    // Protected by PROVIDER_LOCK
    private ModuleAssayCollections _moduleAssayCollections = null;

    ModuleAssayCollections getModuleAssayCollections()
    {
        synchronized (PROVIDER_LOCK)
        {
            if (null == _moduleAssayCollections)
                _moduleAssayCollections = new ModuleAssayCollections();

            return _moduleAssayCollections;
        }
    }

    void clearModuleAssayCollections()
    {
        synchronized (PROVIDER_LOCK)
        {
            _moduleAssayCollections = null;
            QueryService.get().updateLastModified();
        }
    }

    class ModuleAssayCollections
    {
        private final List<AssayProvider> _assayProviders = new LinkedList<>();
        private final Map<String, PipelineProvider> _pipelineProviders = new HashMap<>();
        private final Set<String> _runLsidPrefixes = new HashSet<>();
        private final Map<String, AssayProvider> _resultLsidPrefixes = new HashMap<>();

        private ModuleAssayCollections()
        {
            for (Module module : ModuleLoader.getInstance().getModules())
            {
                for (AssayProvider provider : PROVIDER_CACHE.getResourceMap(module))
                {
                    // Validate the provider
                    AssayManager.get().verifyLegalName(provider);
                    if (AssayManager.get().getProvider(provider.getName(), AssayManager.get().getRegisteredAssayProviders()) != null) // Check against the registered providers
                    {
                        throw new IllegalArgumentException("A provider with the name " + provider.getName() + " has already been registered");
                    }
                    if (AssayManager.get().getProvider(provider.getName(), _assayProviders) != null) // Check against the existing module assay providers
                    {
                        throw new IllegalArgumentException("A module assay provider with the name " + provider.getName() + " already exists");
                    }

                    // Update all the collections with this provider
                    _assayProviders.add(provider);
                    PipelineProvider pipelineProvider = provider.getPipelineProvider();
                    if (pipelineProvider != null)
                    {
                        _pipelineProviders.put(pipelineProvider.getName(), pipelineProvider);
                    }
                    _runLsidPrefixes.add(provider.getRunLSIDPrefix());
                    if (provider.getResultRowLSIDPrefix() != null)
                        _resultLsidPrefixes.put(provider.getResultRowLSIDPrefix(), provider);
                }
            }
        }

        public List<AssayProvider> getAssayProviders()
        {
            return _assayProviders;
        }

        public Map<String, PipelineProvider> getPipelineProviders()
        {
            return _pipelineProviders;
        }

        public Set<String> getRunLsidPrefixes()
        {
            return _runLsidPrefixes;
        }

        public Map<String, AssayProvider> getResultLsidPrefixes()
        {
            return _resultLsidPrefixes;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testModuleResourceCache()
        {
            // Load all the module assay providers to ensure no exceptions and get a count
            int count = PROVIDER_CACHE.streamAllResourceMaps()
                    .mapToInt(Collection::size)
                    .sum();

            LOG.info(count + " assay providers defined in all modules");

            // Make sure the cache retrieves the expected number of assay providers from the miniassay module, if present

            Module miniassay = ModuleLoader.getInstance().getModule("miniassay");

            if (null != miniassay)
                assertEquals("Assay providers from miniassay module", 2, PROVIDER_CACHE.getResourceMap(miniassay).size());
        }
    }
}
