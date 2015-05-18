/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.SimpleWebPartFactory;

import java.nio.file.Path;

/**
 * User: adam
 * Date: 12/29/13
 * Time: 12:38 PM
 */

/**
 * Creates and caches the file-based webparts defined by modules. File changes result in dynamic reloading and re-initization of webpart-related maps.
 */
public class SimpleWebPartFactoryCacheHandler implements ModuleResourceCacheHandler<String, SimpleWebPartFactory>
{
    @Override
    public boolean isResourceFile(String filename)
    {
        return SimpleWebPartFactory.isWebPartFile(filename);
    }

    @Override
    public String getResourceName(Module module, String filename)
    {
        return filename;
    }

    @Override
    public String createCacheKey(Module module, String resourceName)
    {
        return ModuleResourceCache.createCacheKey(module, resourceName);
    }

    @Override
    public CacheLoader<String, SimpleWebPartFactory> getResourceLoader()
    {
        return new CacheLoader<String, SimpleWebPartFactory>()
        {
            @Override
            public SimpleWebPartFactory load(String key, @Nullable Object argument)
            {
                ModuleResourceCache.CacheId tid = ModuleResourceCache.parseCacheKey(key);

                return new SimpleWebPartFactory(tid.getModule(), tid.getName());
            }
        };
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
