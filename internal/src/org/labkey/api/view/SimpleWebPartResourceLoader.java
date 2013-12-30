package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: 12/29/13
 * Time: 1:54 PM
 */
public class SimpleWebPartResourceLoader implements ModuleResourceLoader
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
        SimpleWebPartFactoryCache.get().registerModule(module);
    }
}
