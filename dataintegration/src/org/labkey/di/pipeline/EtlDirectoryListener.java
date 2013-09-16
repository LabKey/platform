package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.resource.FileSystemDirectoryListener;

import java.nio.file.Path;

/**
* User: adam
* Date: 9/14/13
* Time: 11:56 AM
*/
class EtlDirectoryListener implements FileSystemDirectoryListener
{
    private static final Logger LOG = Logger.getLogger(EtlDirectoryListener.class);

    private final Module _module;
    private final TransformManager _transformManager = TransformManager.get();

    public EtlDirectoryListener(Module module)
    {
        _module = module;
    }

    @Override
    public void fileCreated(Path directory, Path entry)
    {
        LOG.info(entry.toString() + " created");
        DescriptorCache.removeConfigNames(_module);
    }

    @Override
    public void fileDeleted(Path directory, Path entry)
    {
        LOG.info(entry.toString() + " deleted");
        DescriptorCache.removeConfigNames(_module);
        removeDescriptor(entry);
    }

    @Override
    public void fileModified(Path directory, Path entry)
    {
        LOG.info(entry.toString() + " updated");
        removeDescriptor(entry);
    }

    @Override
    public void overflow()
    {
        LOG.warn("Overflow!!");
        // I guess we should just clear the entire cache
        DescriptorCache.clear();
    }

    private void removeDescriptor(Path entry)
    {
        String filename = entry.toString();

        if (_transformManager.isConfigFile(filename))
            DescriptorCache.removeDescriptor(_module, _transformManager.getConfigName(filename));
    }
}
