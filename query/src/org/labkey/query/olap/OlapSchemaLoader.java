package org.labkey.query.olap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 10/30/13
 * Time: 11:15 AM
 */
public class OlapSchemaLoader implements ModuleResourceLoader
{
    @NotNull
    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        return Collections.emptySet();
    }

    @Override
    public void registerResources(Module module) throws IOException, ModuleResourceLoadException
    {
        OlapSchemaCache.get().registerModule(module);
    }
}
