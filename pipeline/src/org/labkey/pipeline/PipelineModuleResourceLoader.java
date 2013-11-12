package org.labkey.pipeline;

import edu.emory.mathcs.backport.java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * User: kevink
 * Date: 11/8/13
 */
public class PipelineModuleResourceLoader implements ModuleResourceLoader
{
    @NotNull
    @Override
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        // if (explodedModuleDir contains a 'pipeline' directory)
        // add PipelineModule as a dependency
        return Collections.emptySet();
    }

    @Override
    public void registerResources(Module module) throws IOException, ModuleResourceLoadException
    {
        PipelineJobService.get().registerModule(module);
    }
}
