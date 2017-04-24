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
    void fillResourceRoots(@NotNull Resource topRoot, @NotNull Path path, @NotNull Collection<Resource> roots);

    static ResourceRootProvider chain(ResourceRootProvider primary, ResourceRootProvider secondary)
    {
        return (topRoot, path, roots) ->
        {
            Collection<Resource> primaryRoots = new LinkedList<>();
            primary.fillResourceRoots(topRoot, path, primaryRoots);
            primaryRoots.forEach(root -> secondary.fillResourceRoots(root, path, roots));
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
    ResourceRootProvider STANDARD = (topRoot, path, roots) ->
    {
        Resource standardRoot = topRoot.find(path);

        if (null != standardRoot && standardRoot.isCollection())
            roots.add(standardRoot);
    };

    // Returns the directory at path and all subdirectories, i.e., /resources/<path>/**
    ResourceRootProvider HIERARCHY = (topRoot, path, roots) ->
    {
        Resource standardRoot = topRoot.find(path);

        if (null != standardRoot && standardRoot.isCollection())
            traverse(standardRoot, roots);
    };

    // Returns all immediate subdirectories of the directory at path, i.e., /resources/<path>/*
    ResourceRootProvider SUBDIRECTORIES = (topRoot, path, roots) ->
    {
        Resource standardRoot = topRoot.find(path);

        if (null != standardRoot && standardRoot.isCollection())
        {
            standardRoot.list().stream()
                .filter(Resource::isCollection)
                .forEach(roots::add);
        }
    };

    // Returns resource roots associated with every assay provider folder under /assay, for example:
    //
    // /resources/assay/provider1/<path>
    // /resources/assay/provider2/<path>
    ResourceRootProvider ASSAY_PROVIDERS = (topRoot, path, roots) ->
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

    // Returns all immediate subdirectories of the "queries" directory, i.e., /resources/queries/*. Ignores path.
    ResourceRootProvider QUERY_SUBDIRECTORIES = (topRoot, path, roots) ->
    {
        SUBDIRECTORIES.fillResourceRoots(topRoot, Path.parse(QueryService.MODULE_QUERIES_DIRECTORY), roots);
    };

    // Returns the "queries" directory and all subdirectories, i.e., /resources/queries/**. Ignores path.
    ResourceRootProvider QUERY = (topRoot, path, roots) ->
    {
        HIERARCHY.fillResourceRoots(topRoot, Path.parse(QueryService.MODULE_QUERIES_DIRECTORY), roots);
    };
}
