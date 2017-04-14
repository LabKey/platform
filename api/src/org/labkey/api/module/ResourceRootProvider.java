package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

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

    // Returns resource roots associated with every assay provider folder in the module, for example:
    //
    // /resources/assay/provider1/<path>
    // /resources/assay/provider2/<path>
    ResourceRootProvider ASSAY = (topRoot, path) ->
    {
        List<Resource> roots = new LinkedList<>();

        Resource assayRoot = topRoot.find("assay");

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
}
