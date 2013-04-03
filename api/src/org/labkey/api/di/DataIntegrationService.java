package org.labkey.api.di;

import org.labkey.api.module.Module;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-04-03
 * Time: 11:34 AM
 */
public interface DataIntegrationService
{
    void registerDescriptors(Module module, Collection<ScheduledPipelineJobDescriptor> descriptor);
    public Collection<ScheduledPipelineJobDescriptor> loadDescriptorsFromFiles(Module module, boolean autoRegister);
}
