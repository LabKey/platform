package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resource;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;


/**
 * Created by adam on 4/12/2017.
 */
public interface ResourceRootProvider
{
    Stream<Resource> getResourceRoots(@NotNull Resource topRoot, @NotNull Path path);

    // Returns a single resource root in the standard location, i.e., /resources/<path>
    ResourceRootProvider STANDARD = (topRoot, path) ->
    {
        Resource standardRoot = topRoot.find(path);

        if (null != standardRoot && standardRoot.isCollection())
            return Stream.of(standardRoot);
        else
            return Stream.empty();
    };

    // Returns all directories traversing from the standard location, i.e., /resources/<path>/**
    ResourceRootProvider STANDARD_HIERARCHY = new ResourceRootProvider()
    {
        @Override
        public Stream<Resource> getResourceRoots(@NotNull Resource topRoot, @NotNull Path path)
        {
            Resource standardRoot = topRoot.find(path);
            Collection<Resource> roots = new LinkedList<>();

            if (null != standardRoot && standardRoot.isCollection())
                traverse(standardRoot, roots);

            return roots.isEmpty() ? Stream.empty() : roots.stream();
        }

        private void traverse(Resource dir, Collection<Resource> roots)
        {
            roots.add(dir);

            dir.list().forEach(r ->
            {
                if (r.isCollection())
                    traverse(r, roots);
            });
        }
    };

    // Returns all immediate subdirectories of the standard location, i.e., /resources/<path>/*
    ResourceRootProvider STANDARD_SUBDIRECTORIES = (topRoot, path) ->
    {
        Resource standardRoot = topRoot.find(path);
        Collection<Resource> roots = new LinkedList<>();

        if (null != standardRoot && standardRoot.isCollection())
        {
            standardRoot.list().forEach(r ->
            {
                if (r.isCollection())
                    roots.add(r);
            });
        }

        return roots.isEmpty() ? Stream.empty() : roots.stream();
    };

    // Returns resource roots associated with every assay provider folder under /assay, for example:
    //
    // /resources/assay/provider1/<path>
    // /resources/assay/provider2/<path>
    ResourceRootProvider ASSAY = (topRoot, path) ->
    {
        List<Resource> roots = new LinkedList<>();

        Resource assayRoot = topRoot.find(AssayService.ASSAY_DIR_NAME);

        if (null != assayRoot && assayRoot.isCollection())
        {
            for (Resource root : assayRoot.list())
            {
                if (root.isCollection())
                {
                    Resource r = root.find(path);

                    if (null != r && r.isCollection())
                        roots.add(r);
                }
            }
        }

        return roots.isEmpty() ? Stream.empty() : roots.stream();
    };

    // Returns all immediate subdirectories of the "queries" directory, i.e., /resources/queries/*. Ignores path.
    ResourceRootProvider QUERY = (topRoot, path) ->
    {
        List<Resource> roots = new LinkedList<>();

        Resource queryRoot = topRoot.find(QueryService.MODULE_QUERIES_DIRECTORY);

        if (null != queryRoot && queryRoot.isCollection())
        {
            for (Resource root : queryRoot.list())
            {
                if (root.isCollection())
                {
                    roots.add(root);
                }
            }
        }

        return roots.isEmpty() ? Stream.empty() : roots.stream();
    };
}
