package org.labkey.query.reports;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.PathBasedModuleResourceCache;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *  This cache will replace the complex module report loading and caching code in ReportServiceImpl. Once complete, we should be able to remove that
 *  code, report-specific functionality in Module/DefaultModule (e.g., getCachedReport(Path), cacheReport(Path, ReportDescriptor), getReportFiles(),
 *  preloadReports(), moduleReportFilter), ReportDescriptor.isStale(), etc. This class is not ready to be used and not tested.
 *
 *  Created by adam on 9/12/2016.
 */
public class ModuleReportCache
{
    private static final PathBasedModuleResourceCache<Map<String, ReportDescriptor>> MODULE_REPORT_DESCRIPTOR_CACHE = ModuleResourceCaches.create("Module report cache", new ModuleReportDescriptorHandler());

    static Map<String, ReportDescriptor> getReports(Module module, Path path)
    {
        return MODULE_REPORT_DESCRIPTOR_CACHE.getResource(module, path);
    }

    private static class ModuleReportDescriptorHandler implements ModuleResourceCacheHandler<Path, Map<String, ReportDescriptor>>
    {
        @Override
        public boolean isResourceFile(String filename)
        {
            // TODO: Move moduleReportFilter out of DefaultModule and reconcile with this check
            return StringUtils.endsWithIgnoreCase(filename, ".xml") ||
                    ModuleRReportDescriptor.accept(filename) ||
                    StringUtils.endsWithIgnoreCase(filename, ModuleJavaScriptReportDescriptor.FILE_EXTENSION) ||
                    StringUtils.endsWithIgnoreCase(filename, ModuleQueryReportDescriptor.FILE_EXTENSION);
        }

        @Override
        public String getResourceName(Module module, String filename)
        {
            // We're invalidating the whole list of reports, not individual reports... so leave resource name blank
            return "";
        }

        @Override
        public String createCacheKey(Module module, Path path)
        {
            // We're retrieving/caching/invalidating a list of reports, not individual reports, so append "*" to the
            // requested path. This causes the listener to be registered in "path", not its parent.
            return ModuleResourceCache.createCacheKey(module, path.append("*").toString());
        }

        @Override
        public CacheLoader<String, Map<String, ReportDescriptor>> getResourceLoader()
        {
            return (key, argument) ->
            {
                ModuleResourceCache.CacheId id = ModuleResourceCache.parseCacheKey(key);

                String name = id.getName();
                Module module = id.getModule();

                // Remove "/*" added by getCacheKey()
                Resource queryDir = module.getModuleResource(name.substring(0, name.length() - 2));

                return getReportResources(module, queryDir);
            };
        }

        private Map<String, ReportDescriptor> getReportResources(Module module, Resource queryDir)
        {
            if (queryDir == null || !queryDir.isCollection())
                return Collections.emptyMap();

            Collection<? extends Resource> resources = queryDir.list();

            if (resources.isEmpty())
                return Collections.emptyMap();

            Map<String, ReportDescriptor> descriptors = new HashMap<>();
            HashMap<String, Resource> possibleQueryReportFiles = new HashMap<>();

            for (Resource resource : resources)
            {
                String name = resource.getName();

                if (resource.isFile() && isResourceFile(name))
                {
                    if (StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION))
                        possibleQueryReportFiles.put(name, resource);
                    else
                        descriptors.put(name, ReportServiceImpl.createModuleReportDescriptorInstance(module, resource, null, null));  // TODO: Should have version that doesn't take Container and User
                }
            }

            descriptors.values()
                .stream()
                .filter(descriptor -> null != descriptor.getMetaDataFile())
                .forEach(descriptor -> possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName()));

            // Anything left in this map should be a Query Report
            for (Resource resource : possibleQueryReportFiles.values())
            {
                descriptors.put(resource.getName(), ReportServiceImpl.createModuleReportDescriptorInstance(module, resource, null, null));  // TODO: Should have version that doesn't take Container and User
            }

            return Collections.unmodifiableMap(descriptors);
        }

        @Nullable
        @Override
        public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
        {
            return null;
        }
    }
}
