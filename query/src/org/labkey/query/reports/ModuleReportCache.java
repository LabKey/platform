/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.query.reports;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.io.FilenameFilter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 *  This class handles loading and caching of module reports. For each module, it loads and caches an instance of
 *  ReportsCollections, which can look up a single report by path, all reports associated with a given query, or all
 *  reports defined in the module. MODULE_REPORT_DESCRIPTOR_CACHE invalidates a module's ReportsCollections for any
 *  change to the report files in that module's resources folder.
 *
 *  Created by adam on 9/12/2016.
 */
public class ModuleReportCache
{
    private static final Logger LOG = Logger.getLogger(ModuleResourceCache.class);
    private static final String REPORT_PATH_STRING = "reports/schemas";
    private static final Path REPORT_PATH = Path.parse(REPORT_PATH_STRING);
    private static final ReportCollections EMPTY_REPORT_COLLECTIONS = new ReportCollections(Collections.emptyMap());
    private static final FilenameFilter moduleReportFilter = (dir, name) -> ModuleRReportDescriptor.accept(name) || StringUtils.endsWithIgnoreCase(name, ModuleJavaScriptReportDescriptor.FILE_EXTENSION);
    private static final FilenameFilter moduleReportFilterWithQuery = (dir, name) -> moduleReportFilter.accept(dir, name) || StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION);
    private static final ModuleResourceCache<ReportCollections> MODULE_REPORT_DESCRIPTOR_CACHE = ModuleResourceCaches.create("Module report cache", new ModuleReportHandler(), ResourceRootProvider.getHierarchy(REPORT_PATH));

    @Nullable
    static ReportDescriptor getModuleReportDescriptor(Module module, String path)
    {
        ReportCollections collections = MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(module);
        return collections.getDescriptor(path);
    }

    @NotNull
    static List<ReportDescriptor> getModuleReportDescriptors(Module module, @Nullable String path)
    {
        ReportCollections collections = MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(module);
        return collections.getDescriptors(path);
    }


    private static class ReportCollections
    {
        private final ListValuedMap<String, ReportDescriptor> _mmap;
        private final Map<String, ReportDescriptor> _map;

        private ReportCollections(Map<Path, ReportDescriptor> descriptors)
        {
            CaseInsensitiveArrayListValuedMap<ReportDescriptor> mmap = new CaseInsensitiveArrayListValuedMap<>();
            Map<String, ReportDescriptor> map = new HashMap<>();

            descriptors.forEach((key, value) ->
            {
                map.put(key.toString(), value);
                Path path = key.getParent();
                String subpath = path.subpath(2, path.size()).toString();
                mmap.put(subpath, value);
            });

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

            // Not found... maybe path is relative to "reports/schemas" (e.g., "/lists/People/Less Cool JS Report.js")
            // e.g., http://localhost:8080/labkey/home/list-grid.view?name=People&query.reportId=module%3Asimpletest%2Flists%2FPeople%2FLess%20Cool%20JS%20Report.js (with simpletest active and People list in home)
            if (null == descriptor)
            {
                Path relPath = Path.parse(path);
                Path absPath = REPORT_PATH.append(relPath);
                descriptor = _map.get(absPath.toString());

                // TODO: Eliminate this option... no need to support!
                // Not found... maybe path is relative to "reports" (e.g., "schemas/lists/People/Less Cool JS Report.js"). See #15966.
                // e.g., http://localhost:8080/labkey/home/list-grid.view?name=People&query.reportId=module%3Asimpletest%2Fschemas%2Flists%2FPeople%2FLess%20Cool%20JS%20Report.js (with simpletest active and People list in home)
                // See also SNPRC_EHRTest.testAnimalHistoryReports (SQL Server only... will fail attempting to load "schemas/study/Pedigree/Pedigree.r"
                if (null == descriptor)
                {
                    absPath = REPORT_PATH.getParent().append(relPath)   ;
                    descriptor = _map.get(absPath.toString());
                }
            }

            return descriptor;
        }

        // For testing
        private int size()
        {
            return _map.size();
        }
    }


    private static class ModuleReportHandler implements ModuleResourceCacheHandler<ReportCollections>
    {
        @Override
        public ReportCollections load(Stream<? extends Resource> resources, Module module)
        {
            Map<Path, ReportDescriptor> descriptors = new HashMap<>();
            HashMap<Path, Resource> possibleQueryReportFiles = new HashMap<>();

            resources.forEach(resource -> {
                String name = resource.getName();

                if (isResourceFile(name))
                {
                    if (StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION))
                        possibleQueryReportFiles.put(resource.getPath(), resource);
                    else
                        descriptors.put(resource.getPath(), createModuleReportDescriptorInstance(module, resource));
                }
            });

            descriptors.values().stream()
                .filter(descriptor -> null != descriptor.getMetaDataFile())
                .forEach(descriptor -> possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getPath()));

            // Anything left in this map should be a Query Report
            for (Resource resource : possibleQueryReportFiles.values())
            {
                descriptors.put(resource.getPath(), createModuleReportDescriptorInstance(module, resource));
            }

            return descriptors.isEmpty() ? EMPTY_REPORT_COLLECTIONS : new ReportCollections(descriptors);
        }

        private boolean isResourceFile(String filename)
        {
            return moduleReportFilterWithQuery.accept(null, filename);
        }

        // TODO: Create and register factories for these descriptors
        private static @NotNull ReportDescriptor createModuleReportDescriptorInstance(Module module, Resource reportFile)
        {
            Path path = reportFile.getPath();
            String parent = path.getParent().toString();
            String lower = path.toString().toLowerCase();

            // Create R Report Descriptor
            if (ModuleQueryRReportDescriptor.accept(lower))
                return new ModuleQueryRReportDescriptor(module, parent, reportFile, path);

            // Create JS Report Descriptor
            if (lower.endsWith(ModuleQueryJavaScriptReportDescriptor.FILE_EXTENSION))
                return new ModuleQueryJavaScriptReportDescriptor(module, parent, reportFile, path);

            // Create Query Report Descriptor
            return new ModuleQueryReportDescriptor(module, parent, reportFile, path);
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testModuleResourceCache()
        {
            // Load all the report descriptors to ensure no exceptions
            int descriptorCount = MODULE_REPORT_DESCRIPTOR_CACHE.streamAllResourceMaps()
                .mapToInt(ReportCollections::size)
                .sum();

            LOG.info(descriptorCount + " report descriptors defined in all modules");

            // Make sure the cache retrieves the expected number of report descriptors from a couple test modules, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("Report descriptors from the simpletest module", 5, MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(simpleTest).size());

            Module scriptpad = ModuleLoader.getInstance().getModule("scriptpad");

            if (null != scriptpad)
                assertEquals("Report descriptors from the scriptpad module", 10, MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(scriptpad).size());
        }
    }
}
