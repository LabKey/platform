package org.labkey.di;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * User: adam
 * Date: 9/12/13
 * Time: 2:42 PM
 */
public class EtlResourceLoader implements ModuleResourceLoader
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
        DataIntegrationService dis = ServiceRegistry.get(DataIntegrationService.class);
        if (null != dis)
            dis.registerDescriptorsFromFiles(module);
    }
}
