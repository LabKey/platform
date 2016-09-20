package org.labkey.query.reports;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.PathBasedModuleResourceCache;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *  This cache will replace the complex module report loading and caching code that used to reside in ReportServiceImpl (recently moved to
 *  the bottom of this file). Once complete, we should be able to remove that code, report-specific functionality in Module/DefaultModule
 *  (e.g., getCachedReport(Path), cacheReport(Path, ReportDescriptor), getReportFiles(), preloadReports(), moduleReportFilter),
 *  ReportDescriptor methods like isStale(), and ModuleReportResource.ensureScriptCurrent() The new cache is not ready to be tested.
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
                        descriptors.put(name, createModuleReportDescriptorInstance(module, resource, null, null));  // TODO: Should have version that doesn't take Container and User
                }
            }

            descriptors.values()
                .stream()
                .filter(descriptor -> null != descriptor.getMetaDataFile())
                .forEach(descriptor -> possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName()));

            // Anything left in this map should be a Query Report
            for (Resource resource : possibleQueryReportFiles.values())
            {
                descriptors.put(resource.getName(), createModuleReportDescriptorInstance(module, resource, null, null));  // TODO: Should have version that doesn't take Container and User
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

    @Nullable
    public static ReportDescriptor getModuleReportDescriptor(Module module, Container container, User user, String path)
    {
        ReportDescriptor old = getModuleReportDescriptorOLD(module, container, user, path);

        return old;
    }

    @NotNull
    public static List<ReportDescriptor> getModuleReportDescriptors(Module module, Container container, User user, @Nullable String path)
    {
        List<ReportDescriptor> old = getModuleReportDescriptorsOLD(module, container, user, path);

        return old;
    }

    /* ===== Once new cache is implemented and tested, delete everything below this point ===== */

    @Nullable
    private static ReportDescriptor getModuleReportDescriptorOLD(Module module, Container container, User user, String path)
    {
        List<ReportDescriptor> ds = getModuleReportDescriptorsOLD(module, container, user, path);
        if (ds.size() == 1)
            return ds.get(0);
        return null;
    }

    @NotNull
    private static List<ReportDescriptor> getModuleReportDescriptorsOLD(Module module, Container container, User user, @Nullable String path)
    {
        if (module.getReportFiles().isEmpty())
        {
            return Collections.emptyList();
        }
        else if (null == path)
        {
            return getReportDescriptors(module.getReportFiles(), module, container, user);
        }

        Path legalPath = Path.parse(path);
        legalPath = getLegalFilePath(legalPath);

        // module relative file path
        Resource reportDirectory = module.getModuleResource(legalPath);
        Path moduleReportDirectory = getQueryReportsDirectory(module).getPath();

        // report folder relative file path
        if (null == reportDirectory)
        {
            reportDirectory = module.getModuleResource(moduleReportDirectory.append(legalPath));

            // The directory does not exist
            if (null == reportDirectory)
            {
                // 15966 -- check to see if it is resolving from parent report directory
                reportDirectory = module.getModuleResource(moduleReportDirectory.getParent().append(legalPath));

                if (null == reportDirectory)
                    return Collections.emptyList();
            }
        }

        // Check if it is a file
        if (!reportDirectory.isFile())
        {
            // Not a file so must be within the valid module report path
            if (!reportDirectory.getPath().startsWith(moduleReportDirectory))
                return Collections.emptyList();
        }
        else
        {
            // cannot access files outside of report directory
            if (!reportDirectory.getPath().startsWith(moduleReportDirectory))
                return Collections.emptyList();

            // It is a file so iterate across all files within this file's parent folder.
            reportDirectory = module.getModuleResource(reportDirectory.getPath().getParent());
        }

        List<ReportDescriptor> reportDescriptors = getReportDescriptors(reportDirectory.list(), module, container, user);

        for (ReportDescriptor descriptor : reportDescriptors)
        {
            if (((ModuleReportDescriptor) descriptor).getReportPath().getName().equals(legalPath.getName()))
                return Collections.singletonList(descriptor);
        }

        return reportDescriptors;
    }

    private static List<ReportDescriptor> getReportDescriptors(Collection<? extends Resource> reportFiles, Module module, Container container, User user)
    {
        reportFiles = reportFiles.stream().filter(Resource::exists).collect(Collectors.toList());

        // Keep files that might be Query reports (end in .xml);
        // below we'll remove ones that are associated with R or JS reports
        Map<String, Resource> possibleQueryReportFiles = reportFiles
            .stream()
            .filter(file -> StringUtils.endsWithIgnoreCase(file.getName(), ModuleQueryReportDescriptor.FILE_EXTENSION))
            .collect(Collectors.toMap(Resource::getName, file->file));

        List<ReportDescriptor> reportDescriptors = new ArrayList<>(reportFiles.size());

        for (Resource file : reportFiles)
        {
            if (!DefaultModule.moduleReportFilter.accept(null, file.getName()))
                continue;

            ReportDescriptor descriptor = getReportDescriptor(module, file, container, user);
            reportDescriptors.add(descriptor);

            if (null != descriptor.getMetaDataFile())
                possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName());
        }

        // Anything left in this map should be a Query Report
        possibleQueryReportFiles
            .values()
            .forEach(file -> reportDescriptors.add(getReportDescriptor(module, file, container, user)));

        return reportDescriptors;
    }

    private static ReportDescriptor getReportDescriptor(Module module, Resource file, Container container, User user)
    {
        ReportDescriptor descriptor = module.getCachedReport(file.getPath());

        // cache miss
        if (null == descriptor || descriptor.isStale())
        {
            descriptor = createModuleReportDescriptorInstance(module, file, container, user);

            // NOTE: getLegalFilePath() is not a two-way mapping, this can cause inconsistencies
            // so don't cache files with _ (underscore) in path
            if (!file.getPath().toString().contains("_"))
                module.cacheReport(file.getPath(), descriptor);
        }

        descriptor.setContainer(container.getId());

        return descriptor;
    }

    @NotNull
    private static ReportDescriptor createModuleReportDescriptorInstance(Module module, Resource reportFile, Container container, User user)
    {
        Path path = reportFile.getPath();
        String parent = path.getParent().toString("","");
        String lower = path.toString().toLowerCase();

        // Create R Report Descriptor
        if (ModuleQueryRReportDescriptor.accept(lower))
            return new ModuleQueryRReportDescriptor(module, parent, reportFile, path, container, user);

        // Create JS Report Descriptor
        if (lower.endsWith(ModuleQueryJavaScriptReportDescriptor.FILE_EXTENSION))
            return new ModuleQueryJavaScriptReportDescriptor(module, parent, reportFile, path, container, user);

        // Create Query Report Descriptor
        return new ModuleQueryReportDescriptor(module, parent, reportFile, path, container, user);
    }

    private static Resource getQueryReportsDirectory(Module module)
    {
        return module.getModuleResource("reports/schemas");
    }

    private static Path getLegalFilePath(@NotNull Path key)
    {
        Path legalPath = Path.emptyPath;

        for (int idx = 0; idx < key.size() ; ++idx)
            legalPath = legalPath.append(FileUtil.makeLegalName(key.get(idx)));

        return legalPath;
    }

    private static boolean equals(ReportDescriptor rpt1, ReportDescriptor rpt2)
    {
        if (rpt1.getClass() == rpt2.getClass())
        {
            if (Objects.equals(rpt1.getAuthor(), rpt2.getAuthor()))
            {
                if (Objects.equals(rpt1.getCategory(), rpt2.getCategory()))
                {
                    if (rpt1.getReportName().equals(rpt2.getReportName()) &&
                            rpt1.getDescriptorType().equals(rpt2.getDescriptorType()) &&
                            rpt1.getFlags() == rpt2.getFlags() &&
                            rpt1.getAccess().equals(rpt2.getAccess()) &&
                            rpt1.getDisplayOrder() == rpt2.getDisplayOrder() &&
                            rpt1.getReportKey().equals(rpt2.getReportKey()))
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
