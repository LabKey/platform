/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resource;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.LinkedList;


/**
 * Created by adam on 4/12/2017.
 */
public interface ResourceRootProvider
{
    void fillResourceRoots(@NotNull Resource topRoot, @NotNull Collection<Resource> roots);

    static ResourceRootProvider chain(ResourceRootProvider primary, ResourceRootProvider secondary)
    {
        return (topRoot, roots) ->
        {
            Collection<Resource> primaryRoots = new LinkedList<>();
            primary.fillResourceRoots(topRoot, primaryRoots);
            primaryRoots.forEach(root -> secondary.fillResourceRoots(root, roots));
        };
    }

    /**
     * Recursively traverse all subdirectories of a directory, adding them all to the roots collection. This is a helper
     * method meant for use by ResourceRootProvider implementations.
     * @param dir A collection (directory) resource
     * @param roots A collection to which the method will add resources mapping to the current directory and all subdirectories
     */
    static void traverse(Resource dir, Collection<Resource> roots)
    {
        roots.add(dir);

        for (Resource r : dir.list())
            if (r.isCollection())
                traverse(r, roots);
    }

    // Returns a single resource root, the directory at path, i.e., /resources/<path>
    static ResourceRootProvider getStandard(Path path)
    {
        return (topRoot, roots) ->
        {
            Resource standardRoot = topRoot.find(path);

            if (null != standardRoot && standardRoot.isCollection())
                roots.add(standardRoot);
        };
    }

    // Returns the directory at path and all subdirectories, i.e., /resources/<path>/**
    static ResourceRootProvider getHierarchy(Path path)
    {
        return (topRoot, roots) ->
        {
            Resource standardRoot = topRoot.find(path);

            if (null != standardRoot && standardRoot.isCollection())
                traverse(standardRoot, roots);
        };
    }

    static ResourceRootProvider getSubdirectories(Path path)
    {
        return (topRoot, roots) ->
        {
            Resource standardRoot = topRoot.find(path);

            if (null != standardRoot && standardRoot.isCollection())
            {
                standardRoot.list().stream()
                    .filter(Resource::isCollection)
                    .forEach(roots::add);
            }
        };
    }

    // Returns resource roots associated with every assay provider folder under /assay, for example:
    //
    // /resources/assay/provider1/<path>
    // /resources/assay/provider2/<path>
    static ResourceRootProvider getAssayProviders(Path path)
    {
        return (topRoot, roots) ->
        {
            Resource assayRoot = topRoot.find(AssayService.ASSAY_DIR_NAME);

            if (null != assayRoot && assayRoot.isCollection())
            {
                assayRoot.list().stream()
                    .filter(Resource::isCollection)
                    .map(providerDir -> providerDir.find(path))
                    .filter(r -> null != r && r.isCollection())
                    .forEach(roots::add);
            }
        };
    }

    // Returns all immediate subdirectories of the "queries" directory, i.e., /resources/queries/*.
    ResourceRootProvider QUERY_SUBDIRECTORIES = getSubdirectories(QueryService.MODULE_QUERIES_PATH);

    // Returns the "queries" directory and all subdirectories, i.e., /resources/queries/**.
    ResourceRootProvider QUERY = getHierarchy(QueryService.MODULE_QUERIES_PATH);
}
