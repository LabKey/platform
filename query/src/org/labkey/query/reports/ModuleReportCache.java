package org.labkey.query.reports;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 *  This cache replaces the complex module report loading and caching code that used to reside in ReportServiceImpl (recently moved to
 *  the bottom of this file). Once complete, we should be able to remove that code, report-specific functionality in Module/DefaultModule
 *  (e.g., getCachedReport(Path), cacheReport(Path, ReportDescriptor), getReportFiles(), preloadReports(), moduleReportFilter),
 *  ReportDescriptor methods like isStale(), and ModuleReportResource.ensureScriptCurrent(). The new cache is implemented, but the file
 *  listeners still need to be registered on all visited directories.
 *
 *  Created by adam on 9/12/2016.
 */
public class ModuleReportCache
{
    private static final Logger LOG = Logger.getLogger(ModuleReportCache.class);
    private static final ModuleResourceCache<ReportCollections> MODULE_REPORT_DESCRIPTOR_CACHE = ModuleResourceCaches.create(new Path(), "Module report cache", new ModuleReportHandler());

    private static final FilenameFilter moduleReportFilter = (dir, name) ->
        ModuleRReportDescriptor.accept(name) || StringUtils.endsWithIgnoreCase(name, ModuleJavaScriptReportDescriptor.FILE_EXTENSION);

    private static final FilenameFilter moduleReportFilterWithQuery = (dir, name) ->
        moduleReportFilter.accept(dir, name) || StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION);

    private static final String REPORT_PATH_STRING = "reports/schemas";
    private static final Path REPORT_PATH = Path.parse(REPORT_PATH_STRING);
    private static final ReportCollections EMPTY_REPORT_COLLECTIONS = new ReportCollections(new CaseInsensitiveArrayListValuedMap<>(), new HashMap<>());

    static List<ReportDescriptor> getDescriptors(Module module, @Nullable String path, Container c, User user)
    {
        return MODULE_REPORT_DESCRIPTOR_CACHE.getResource(module.getName(), new DefaultContainerUser(c, user)).getDescriptors(path);
    }

    static @Nullable ReportDescriptor getDescriptor(Module module, String path, Container c, User user)
    {
        return MODULE_REPORT_DESCRIPTOR_CACHE.getResource(module.getName(), new DefaultContainerUser(c, user)).getDescriptor(path);
    }

    private static class ReportCollections
    {
        private final ListValuedMap<String, ReportDescriptor> _mmap;
        private final Map<String, ReportDescriptor> _map;

        private ReportCollections(CaseInsensitiveArrayListValuedMap<ReportDescriptor> mmap, Map<String, ReportDescriptor> map)
        {
            mmap.trimToSize();
            _mmap = mmap;
            _map = map;
        }

        private List<ReportDescriptor> getDescriptors(@Nullable String path)
        {
            if (null == path)
                return new LinkedList<>(_mmap.values());

            // Make all paths relative
            if (path.startsWith(REPORT_PATH_STRING))
                path = path.substring(REPORT_PATH_STRING.length());

            return _mmap.get(path);
        }

        private @Nullable ReportDescriptor getDescriptor(String path)
        {
            ReportDescriptor descriptor = _map.get(path);

            // Not found... maybe path is relative to "reports/schemas"
            // e.g., http://localhost:8080/labkey/home/list-grid.view?name=People&query.reportId=module%3Asimpletest%2Flists%2FPeople%2FLess%20Cool%20JS%20Report.js (with simpletest active and People list in home)
            if (null == descriptor)
            {
                Path relPath = Path.parse(path);
                Path absPath = REPORT_PATH.append(relPath);
                descriptor = _map.get(absPath.toString());

                // Not found... maybe path is relative to "reports", #15966
                // e.g., http://localhost:8080/labkey/home/list-grid.view?name=People&query.reportId=module%3Asimpletest%2Fschemas%2Flists%2FPeople%2FLess%20Cool%20JS%20Report.js (with simpletest active and People list in home)
                if (null == descriptor)
                {
                    absPath = REPORT_PATH.getParent().append(relPath)   ;
                    descriptor = _map.get(absPath.toString());
                }
            }

            return descriptor;
        }
    }

    private static class ModuleReportHandler implements ModuleResourceCacheHandler<String, ReportCollections>
    {
        @Override
        public boolean isResourceFile(String filename)
        {
            return moduleReportFilterWithQuery.accept(null, filename);
        }

        @Override
        public String getResourceName(Module module, String filename)
        {
            // We're invalidating the whole list of reports, not individual reports... so leave resource name blank
            return "";
        }

        @Override
        public String createCacheKey(Module module, String resourceLocation)
        {
            // We're retrieving/caching/invalidating a list of reports, not individual reports, so append "*" to the
            // requested path. This causes the listener to be registered in "path", not its parent.
            return ModuleResourceCache.createCacheKey(module, "*");
        }

        @Override
        public CacheLoader<String, ReportCollections> getResourceLoader()
        {
            return new CacheLoader<String, ReportCollections>()
            {
                @Override
                public ReportCollections load(String moduleName, Object argument)
                {
                    // TODO: Remove this hack... shouldn't be passing in Container or User. But needed for now to validate new vs. old caching approach. #
                    ContainerUser cu = (ContainerUser)argument;
                    Container c = cu.getContainer();
                    User user = cu.getUser();
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    Resource reportsDir = getQueryReportsDirectory(module);

                    if (null == reportsDir || !reportsDir.isCollection())
                        return EMPTY_REPORT_COLLECTIONS;

                    CaseInsensitiveArrayListValuedMap<ReportDescriptor> mmap = new CaseInsensitiveArrayListValuedMap<>();
                    Map<String, ReportDescriptor> map = new HashMap<>();
                    addReports(module, reportsDir, map, mmap, c, user);

                    return new ReportCollections(mmap, map);
                }

                private void addReports(Module module, Resource dir, Map<String, ReportDescriptor> map, MultiValuedMap<String, ReportDescriptor> mmap, Container c, User user)
                {
                    Map<Path, ReportDescriptor> descriptors = new HashMap<>();
                    HashMap<String, Resource> possibleQueryReportFiles = new HashMap<>();
                    List<Resource> directories = new LinkedList<>();

                    for (Resource resource : dir.list())
                    {
                        if (resource.isCollection())
                        {
                            directories.add(resource);
                        }
                        else
                        {
                            String name = resource.getName();

                            if (isResourceFile(name))
                            {
                                if (StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION))
                                    possibleQueryReportFiles.put(name, resource);
                                else
                                    descriptors.put(resource.getPath(), createModuleReportDescriptorInstance(module, resource, c, user));  // TODO: Should have version that doesn't take Container and User
                            }
                        }
                    }

                    descriptors.values()
                        .stream()
                        .filter(descriptor -> null != descriptor.getMetaDataFile())
                        .forEach(descriptor -> possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName()));

                    // Anything left in this map should be a Query Report
                    for (Resource resource : possibleQueryReportFiles.values())
                    {
                        descriptors.put(resource.getPath(), createModuleReportDescriptorInstance(module, resource, c, user));  // TODO: Should have version that doesn't take Container and User
                    }

                    Path path = dir.getPath();
                    String subpath = path.subpath(2, path.size()).toString();

                    descriptors.entrySet().forEach(entry ->
                    {
                        map.put(entry.getKey().toString(), entry.getValue());
                        mmap.put(subpath, entry.getValue());
                    });

                    for (Resource childDir : directories)
                    {
                        addReports(module, childDir, map, mmap, c, user);
                    }
                }
            };
         }
    }

    @Nullable
    public static ReportDescriptor getModuleReportDescriptor(Module module, Container c, User user, String path)
    {
        ReportDescriptor descriptor = getDescriptor(module, path, c, user);

        // Test against old caching approach in dev mode
        if (AppProps.getInstance().isDevMode())
        {
            ReportDescriptor oldDescriptor = getModuleReportDescriptorOLD(module, c, user, path);
            String difference = differenceNullSafe(oldDescriptor, descriptor);

            if (null != difference)
                log("Module report discrepancy: " + module.getName() + " " + path + ": " + difference);
        }

        return descriptor;
    }

    @NotNull
    public static List<ReportDescriptor> getModuleReportDescriptors(Module module, Container c, User user, @Nullable String path)
    {
        List<ReportDescriptor> descriptors = getDescriptors(module, path, c, user);

        // Test against old caching approach in dev mode
        if (AppProps.getInstance().isDevMode())
        {
            // Sort the old and new lists using the same comparator
            Set<ReportDescriptor> oldSorted = new TreeSet<>((d1, d2) -> d1.toString().compareToIgnoreCase(d2.toString()));
            oldSorted.addAll(getModuleReportDescriptorsOLD(module, c, user, path));
            Set<ReportDescriptor> newSorted = new TreeSet<>((d1, d2) -> d1.toString().compareToIgnoreCase(d2.toString()));
            newSorted.addAll(descriptors);

            if (oldSorted.size() != newSorted.size())
            {
                log("Module report discrepancy: different size lists for " + module.getName() + " " + path + " (old: " + oldSorted.size() + ", new: " + newSorted.size() + ")");
            }
            else
            {
                Iterator<ReportDescriptor> iter = newSorted.iterator();

                oldSorted.forEach(descriptor ->
                {
                    String difference = difference(descriptor, iter.next());

                    if (null != difference)
                        log("Module report discrepancy: \"" + descriptor.getReportName() + "\" " + module.getName() + " " + path + ": " + difference);
                });
            }
        }

        return descriptors;
    }

    private static void log(String message)
    {
        LOG.error(message);
    }

    /* ===== Once new cache is fully tested, delete everything below this point ===== */

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
            if (!moduleReportFilter.accept(null, file.getName()))
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
        String parent = path.getParent().toString();
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
        return module.getModuleResource(REPORT_PATH);
    }

    private static Path getLegalFilePath(@NotNull Path key)
    {
        Path legalPath = Path.emptyPath;

        for (int idx = 0; idx < key.size() ; ++idx)
            legalPath = legalPath.append(FileUtil.makeLegalName(key.get(idx)));

        return legalPath;
    }

    private static @Nullable String differenceNullSafe(ReportDescriptor rpt1, ReportDescriptor rpt2)
    {
        if (null == rpt1 || null == rpt2)
        {
            return rpt1 == rpt2 ? null : "null + non-null";
        }
        return difference(rpt1, rpt2);
    }

    private static @Nullable String difference(ReportDescriptor rpt1, ReportDescriptor rpt2)
    {
        if (rpt1.getClass() != rpt2.getClass())
            return "class";

        if (!Objects.equals(rpt1.getAuthor(), rpt2.getAuthor()))
            return "author";

        if (!Objects.equals(rpt1.getCategory(), rpt2.getCategory()))
            return "category";

        if (!rpt1.getReportName().equals(rpt2.getReportName()))
            return "report name";

        if (!rpt1.getDescriptorType().equals(rpt2.getDescriptorType()))
            return "descriptor type";

        if (rpt1.getFlags() != rpt2.getFlags())
            return "flags";

        if (!rpt1.getAccess().equals(rpt2.getAccess()))
            return "access";

        if (rpt1.getDisplayOrder() != rpt2.getDisplayOrder())
            return "display order";

        if (!rpt1.getReportKey().equals(rpt2.getReportKey()))
            return "report key";

        if (!Objects.equals(rpt1.getResourceDescription(), rpt2.getResourceDescription()))
            return "resource description";

        if (!Objects.equals(rpt1.getProperties().get("script"), rpt2.getProperties().get("script")))
            return "script";

        return null;
    }
}
