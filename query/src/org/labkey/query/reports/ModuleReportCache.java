/*
 * Copyright (c) 2016 LabKey Corporation
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
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache2;
import org.labkey.api.module.ModuleResourceCacheHandler2;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;

import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private static final String REPORT_PATH_STRING = "reports/schemas";
    private static final Path REPORT_PATH = Path.parse(REPORT_PATH_STRING);
    private static final ReportCollections EMPTY_REPORT_COLLECTIONS = new ReportCollections(new CaseInsensitiveArrayListValuedMap<>(), new HashMap<>());
    private static final FilenameFilter moduleReportFilter = (dir, name) -> ModuleRReportDescriptor.accept(name) || StringUtils.endsWithIgnoreCase(name, ModuleJavaScriptReportDescriptor.FILE_EXTENSION);
    private static final FilenameFilter moduleReportFilterWithQuery = (dir, name) -> moduleReportFilter.accept(dir, name) || StringUtils.endsWithIgnoreCase(name, ModuleQueryReportDescriptor.FILE_EXTENSION);

    private static final ModuleResourceCache2<ReportCollections> MODULE_REPORT_DESCRIPTOR_CACHE = new ModuleResourceCache2<>("Module report cache", new ModuleReportHandler(), REPORT_PATH);

    @Nullable
    static ReportDescriptor getModuleReportDescriptor(Module module, Container c, User user, String path)
    {
        ReportCollections collections = MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(module, c, user);
        return collections.getDescriptor(path);
    }

    @NotNull
    static List<ReportDescriptor> getModuleReportDescriptors(Module module, Container c, User user, @Nullable String path)
    {
        ReportCollections collections = MODULE_REPORT_DESCRIPTOR_CACHE.getResourceMap(module, c, user);
        return collections.getDescriptors(path);
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
    }


    private static class ModuleReportHandler implements ModuleResourceCacheHandler2<ReportCollections>
    {
        @Override
        public ReportCollections load(@Nullable Resource dir, Module module, Container c, User user)
        {
            if (null == dir)
                return EMPTY_REPORT_COLLECTIONS;

            CaseInsensitiveArrayListValuedMap<ReportDescriptor> mmap = new CaseInsensitiveArrayListValuedMap<>();
            Map<String, ReportDescriptor> map = new HashMap<>();
            addReports(module, dir, map, mmap, c, user);

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

        private boolean isResourceFile(String filename)
        {
            return moduleReportFilterWithQuery.accept(null, filename);
        }

        // TODO: Create and register factories for these descriptors
        private static @NotNull ReportDescriptor createModuleReportDescriptorInstance(Module module, Resource reportFile, Container container, User user)
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
    }
}
