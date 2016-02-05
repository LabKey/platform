/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.nio.file.StandardWatchEventKinds;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 1/13/14
 * Time: 6:40 PM
 */
public class ModuleResourceCaches
{
    /**
     * Create a new ModuleResourceCache. This is the standard method to use in most situations, where each module has a
     * single directory that contains files that represent a single object type.
     *
     * @param path Path representing the module resource directory
     * @param description Short description of the cache
     * @param handler ModuleResourceCacheHandler that customizes this cache's behavior
     * @param <T> Object type that this cache handles
     * @return A ModuleResourceCache
     */
    public static <T> ModuleResourceCache<T> create(Path path, String description, ModuleResourceCacheHandler<String, T> handler)
    {
        return create(createModuleResourceDirectory(path), description, handler);
    }

    /**
     * Create a new ModuleResourceCache. This method is needed only in cases where multiple caches, handling different
     * object types, need to operate on the same resource directory. This method lets caches share a ModuleResourceDirectory,
     * which shares the underlying FileSystemDirectoryListener.
     *
     * @param directory A ModuleResourceDirectory that's been initialized to a particular path
     * @param description Short description of the cache
     * @param handler ModuleResourceCacheHandler that customizes this cache's behavior
     * @param <T> Object type that this cache handles
     * @return A ModuleResourceCache
     */
    public static <T> ModuleResourceCache<T> create(ModuleResourceDirectory directory, String description, ModuleResourceCacheHandler<String, T> handler)
    {
        ModuleResourceCache<T> cache = new ModuleResourceCache<>(directory, description, handler);
        directory.registerCache(cache);

        return cache;
    }

    public static <T> PathBasedModuleResourceCache<T> create(String description, ModuleResourceCacheHandler<Path, T> handler)
    {
        return new PathBasedModuleResourceCache<>(description, handler);
    }

    /**
     * Create a new QueryBasedModuleResourceCache. This is used to cache file-system resources that are associated with
     * specific queries.
     *
     * @param root Path representing the module resource directory
     * @param description Short description of the cache
     * @param handler QueryBasedModuleResourceCacheHandler that customizes this cache's behavior
     * @param <T> Object type that this cache handles
     * @return A QueryBasedModuleResourceCache
     */
    public static <T> QueryBasedModuleResourceCache<T> createQueryBasedCache(Path root, String description, QueryBasedModuleResourceCacheHandler<T> handler)
    {
        return new QueryBasedModuleResourceCache<>(root, description, handler);
    }

    /**
     * Create a new ModuleResourceDirectory for a given path.
     * @param path Path representing the module resource directory
     * @return A ModuleResourceDirectory
     */
    public static ModuleResourceDirectory createModuleResourceDirectory(Path path)
    {
        return new StandardModuleResourceDirectory(path);
    }


    private static class StandardModuleResourceDirectory implements ModuleResourceDirectory
    {
        private final Path _path;
        private final FileSystemWatcher _watcher;
        // A map of all modules that include the specified resource directory to the standard listener that handles that
        // module + directory combination. This is initialized at construction time and never changed.
        private final Map<Module, StandardModuleResourceListener> _moduleListeners = new ConcurrentHashMap<>();

        private StandardModuleResourceDirectory(Path path)
        {
            _path = path;
            _watcher = FileSystemWatchers.get("Module resource directory " + path.toString());

            for (Module module : ModuleLoader.getInstance().getModules())
                registerModule(module);
        }

        @Override
        public Path getPath()
        {
            return _path;
        }

        @Override
        public Collection<Module> getModules()
        {
            return _moduleListeners.keySet();
        }

        @Override
        public <T> void registerCache(ModuleResourceCache<T> cache)
        {
            for (Module module : getModules())
            {
                StandardModuleResourceListener standardListener = _moduleListeners.get(module);
                assert null != standardListener;
                standardListener.addListener(cache.getListener(module));
            }
        }

        // If this module includes the resource path then register a file listener to monitor for changes.
        // The listener won't actually do anything useful yet; each cache will add the actual cache listeners when
        // registerCache() is called.
        public void registerModule(Module module)
        {
            Resource dirResource = module.getModuleResolver().lookup(_path);

            if (null != dirResource && dirResource.isCollection())
            {
                // At construction time, we register an empty listener on the directory. Each cache associated with the
                // directory will add its listener via addListener()
                StandardModuleResourceListener emptyListener = new StandardModuleResourceListener();
                _moduleListeners.put(module, emptyListener);

                // TODO: Integrate this better with Resource
                ((MergedDirectoryResource)dirResource).registerListener(_watcher, emptyListener,
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            }
        }
    }


    private static class StandardModuleResourceListener implements FileSystemDirectoryListener
    {
        private final List<FileSystemDirectoryListener> _listeners = new CopyOnWriteArrayList<>();

        public void addListener(FileSystemDirectoryListener listener)
        {
            _listeners.add(listener);
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            for (FileSystemDirectoryListener listener : _listeners)
                listener.entryCreated(directory, entry);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            for (FileSystemDirectoryListener listener : _listeners)
                listener.entryDeleted(directory, entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            for (FileSystemDirectoryListener listener : _listeners)
                listener.entryModified(directory, entry);
        }

        @Override
        public void overflow()
        {
            for (FileSystemDirectoryListener listener : _listeners)
                listener.overflow();
        }
    }
}
