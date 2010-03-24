package org.labkey.api.resource;

import org.labkey.api.util.Path;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:30:21 AM
 */
public interface Resolver
{
    Path getRootPath();

    /**
     * Locate a Resource at the given path.
     * @param path
     * @return
     */
    Resource lookup(Path path);
}
