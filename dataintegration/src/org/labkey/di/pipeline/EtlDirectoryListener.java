/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.files.FileSystemDirectoryListener;

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
    public void entryCreated(Path directory, Path entry)
    {
        DescriptorCache.removeConfigNames(_module);
    }

    @Override
    public void entryDeleted(Path directory, Path entry)
    {
        DescriptorCache.removeConfigNames(_module);
        removeDescriptor(entry);
    }

    @Override
    public void entryModified(Path directory, Path entry)
    {
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
