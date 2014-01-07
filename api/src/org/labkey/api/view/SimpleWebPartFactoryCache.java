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
package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.SimpleController;
import org.labkey.api.module.SimpleWebPartFactory;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: 12/29/13
 * Time: 12:38 PM
 */

// TODO: Extend to load file-based views as well??

/**
 * Creates and caches the file-based webparts defined by modules. In dev mode, file changes result in dynamic reloading
 * and re-initization of webpart-related maps.
 */
public class SimpleWebPartFactoryCache extends ModuleResourceCache<SimpleWebPartFactory>
{
    private static final SimpleWebPartFactoryCache _instance = new SimpleWebPartFactoryCache();

    public static SimpleWebPartFactoryCache get()
    {
        return _instance;
    }

    private SimpleWebPartFactoryCache()
    {
        super(new org.labkey.api.util.Path(SimpleController.VIEWS_DIRECTORY), "File-based webpart definitions");
    }

    @Nullable
    @Override
    protected FileSystemDirectoryListener createChainedDirectoryListener(final Module module)
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

    @Override
    protected boolean isResourceFile(String filename)
    {
        return SimpleWebPartFactory.isWebPartFile(filename);
    }

    @Override
    protected String getResourceName(Module module, String filename)
    {
        return filename;
    }

    @Override
    protected String createCacheKey(Module module, String resourceName)
    {
        return module.getName() + "/" + resourceName;
    }

    private static final Pattern CACHE_KEY_PATTERN = Pattern.compile("(\\w+)/(.+)");

    @Override
    protected CacheLoader<String, SimpleWebPartFactory> getResourceLoader()
    {
        return new CacheLoader<String, SimpleWebPartFactory>()
        {
            @Override
            public SimpleWebPartFactory load(String key, @Nullable Object argument)
            {
                Matcher matcher = CACHE_KEY_PATTERN.matcher(key);

                if (!matcher.matches() || matcher.groupCount() != 2)
                    throw new IllegalStateException("Unrecognized webpart cache key format: " + key);

                String moduleName = matcher.group(1);
                String filename = matcher.group(2);
                Module module = ModuleLoader.getInstance().getModule(moduleName);

                if (null == module)
                    throw new IllegalStateException("Module does not exist: " + moduleName);

                return new SimpleWebPartFactory(module, filename);
            }
        };
    }
}
