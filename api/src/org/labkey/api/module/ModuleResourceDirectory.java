package org.labkey.api.module;

import org.labkey.api.util.Path;

import java.util.Collection;

/**
 * User: adam
 * Date: 1/13/14
 * Time: 6:57 PM
 */
public interface ModuleResourceDirectory
{
    Path getPath();
    Collection<Module> getModules();
    <T> void registerCache(ModuleResourceCache<T> cache);
}
