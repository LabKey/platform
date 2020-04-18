/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.files.SupportsFileSystemWatcher;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavService;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ClientDependencyCacheLoader implements CacheLoader<Pair<Path, ModeTypeEnum.Enum>, ClientDependency>
{
    private static final Logger LOG = Logger.getLogger(ClientDependencyCacheLoader.class);
    private static final Set<Path> EXISTING_LISTENERS = new ConcurrentHashSet<>();

    public ClientDependencyCacheLoader()
    {
    }

    @Override
    public @Nullable ClientDependency load(@NotNull Pair<Path, ModeTypeEnum.Enum> key, Object argument)
    {
        Path path = key.first;
        ModeTypeEnum.Enum mode = key.second;

        ClientDependency.TYPE primaryType = ClientDependency.TYPE.fromPath(path);

        if (primaryType == null)
        {
            LOG.warn("Client dependency type not recognized: " + path);
            return null;
        }

        ClientDependency cr;

        if (ClientDependency.TYPE.context == primaryType)
        {
            String moduleName = FileUtil.getBaseName(path.getName());
            Module m = ModuleLoader.getInstance().getModule(moduleName);

            if (m == null)
            {
                ClientDependency.logError("Module \"" + moduleName + "\" not found, skipping script file \"" + path + "\".");
                return null;
            }

            cr = new ContextClientDependency(m, mode);
        }
        else
        {
            Resource r = WebdavService.get().getRootResolver().lookup(path);
            //TODO: can we connect this resource back to a module, and load that module's context by default?

            if (r == null || !r.exists())
            {
                // Allows you to run in dev mode without having the concatenated scripts built
                if (!AppProps.getInstance().isDevMode() || !mode.equals(ModeTypeEnum.PRODUCTION))
                {
                    ClientDependency.logError("ClientDependency \"" + path + "\" not found, skipping.");
                    return null;
                }
            }

            if (ClientDependency.TYPE.lib == primaryType)
            {
                cr = new LibClientDependency(path, mode, r);

                if (null != r)
                    ensureListener(key, r);
            }
            else
            {
                cr = new FilePathClientDependency(path, mode, primaryType);
            }
        }

        cr.init();

        return cr;
    }

    // We're registering listeners only on lib.xml files to invalidate the cache when they change. There aren't many lib files (< 100),
    // so we don't have to be particularly clever... just construct and register one listener per lib file + mode combo.
    private void ensureListener(@NotNull Pair<Path, ModeTypeEnum.Enum> key, @NotNull Resource r)
    {
        if (!EXISTING_LISTENERS.add(key.first))
            return;

        String name = r.getName();

        if (r instanceof SupportsFileSystemWatcher)
        {
            //noinspection unchecked
            ((SupportsFileSystemWatcher) r).registerListenerOnParent(FileSystemWatchers.get(), new FileSystemDirectoryListener()
            {
                @Override
                public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                @Override
                public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                @Override
                public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    process(entry);
                }

                private void process(java.nio.file.Path entry)
                {
                    if (entry.toString().equals(name))
                        ClientDependency.CACHE.remove(key);
                }

                @Override
                public void overflow()
                {
                }
            }, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
    }
}
