/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.SimpleWebPartFactory;
import org.labkey.api.resource.Resource;

import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates and caches the file-based web parts defined by modules. File changes result in dynamic reloading and re-initialization of webpart-related maps.
 * User: adam
 * Date: 12/29/13
 * Time: 12:38 PM
 */
public class SimpleWebPartFactoryCacheHandler implements ModuleResourceCacheHandler<Collection<SimpleWebPartFactory>>
{
    @Override
    public Collection<SimpleWebPartFactory> load(Stream<? extends Resource> resources, Module module)
    {
        return unmodifiable(resources
            .filter(getFilter(SimpleWebPartFactory.FILE_EXTENSION))
            .map(resource -> new SimpleWebPartFactory(module, resource))
            .collect(Collectors.toList())
        );
    }

    @Nullable
    @Override
    public FileSystemDirectoryListener createChainedDirectoryListener(final Module module)
    {
        return new FileSystemDirectoryListener()
        {
            @Override
            public void entryCreated(Path directory, Path entry)
            {
                update();
            }

            @Override
            public void entryDeleted(Path directory, Path entry)
            {
                update();
            }

            @Override
            public void entryModified(Path directory, Path entry)
            {
                update();
            }

            @Override
            public void overflow()
            {
                update();
            }

            private void update()
            {
                Portal.clearWebPartFactories(module);
                Portal.clearMaps();
            }
        };
    }
}
