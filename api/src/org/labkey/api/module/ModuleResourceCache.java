/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceWrapper;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Standard cache for file-system resources provided by a module. An instance of this class manages one specific type of
 * resource (e.g., query, custom view, report, etc.) for all modules. This class loads, returns, and invalidates a single
 * object per module (referred to as a "resource map" below), typically a Map or a bean containing multiple Maps presenting
 * different lookup options to callers. It registers file system listeners in every directory the loader visits and
 * invalidates the object on any file system change that occurs within those directories (update, delete, or add of any
 * file or directory). A single change to a single file will therefore result in reloading all the resources of the given
 * type in that module.
 *
 * Note: This class is a simplification and generalization that should eventually be able to replace ModuleResourceCacheOld,
 * QueryBasedModuleResourceCache, and PathBasedModuleResourceCache. The trade-offs for this simplification: each loader
 * needs to do a little more work (traversing directories, filtering resource files, creating & populating maps) and all
 * resources of a given type for a given module are loaded & invalidated together, as opposed to invalidating individual
 * resources when changes occur. These seem like reasonable trade-offs...
 *
 * User: adam
 * Date: 12/26/13
 */
public final class ModuleResourceCache<V>
{
    private static final Logger LOG = Logger.getLogger(ModuleResourceCache.class);

    private final BlockingCache<Module, V> _cache;
    private final ModuleResourceCacheHandler<V> _handler;
    private final FileSystemWatcher _watcher = FileSystemWatchers.get();
    private final Set<String> _pathsWithListeners = new ConcurrentHashSet<>();
    private final String _description;

    ModuleResourceCache(Path root, ModuleResourceCacheHandler<V> handler, String description)
    {
        CacheLoader<Module, V> wrapper = new CacheLoader<Module, V>()
        {
            @Override
            public V load(Module module, Object argument)
            {
                ModuleResourceCache cache = (ModuleResourceCache)argument;
                Resource dir = module.getModuleResource(root);
                Resource wrappedDir = (null != dir && dir.isCollection() ? new FileListenerResource(dir, module, cache) : null);

                return _handler.load(wrappedDir, module);
            }

            @Override
            public String toString()
            {
                return "CacheLoader for \"" + _description + "\" (" + _handler.getClass().getName() + ")";
            }
        };

        _description = description;
        _cache = CacheManager.getBlockingCache(Constants.getMaxModules(), CacheManager.DAY, _description, wrapper);  // Cache is one entry per module
        _handler = handler;
//        ensureListeners(root);  // TODO: Enable this to ensure file listeners along the root... except that this seems to deadlock. Maybe push this into CacheLoader.load() above?
    }

    // Add a listener in all modules to every directory that's part of this root path. This ensures that the cache will be
    // invalidated if (for example) an entire resource directory is added or removed.
    private void ensureListeners(Path root)
    {
        ModuleLoader.getInstance().getModules().forEach(module -> {
            Path parent;
            while (null != (parent = root.getParent()))
            {
                Resource resource = module.getModuleResource(parent);

                if (null != resource && resource.isCollection())
                    ensureListener(resource, module);
            }
        });
    }

    public @NotNull V getResourceMap(Module module)
    {
        return _cache.get(module, this);
    }

    /**
     *  Return a collection of all resource maps managed by this cache that are defined in the active modules
     *  in the specified Container.
     */
    public @NotNull Collection<V> getResourceMaps(Container c)
    {
        List<V> list = c.getActiveModules().stream().map(this::getResourceMap).collect(Collectors.toList());
        return Collections.unmodifiableCollection(list);
    }

    // Clear a single module's resource map from the cache
    private void removeResourceMap(Module module)
    {
        _cache.remove(module);
    }

    // Clear the whole cache
    private void clear()
    {
        _cache.clear();
    }

    FileSystemDirectoryListener getListener(Module module)
    {
        return new StandardListener(module, _handler.createChainedDirectoryListener(module));
    }

    public void ensureListener(Resource resource, Module module)
    {
        assert resource.isCollection();
        Path path = resource.getPath();

        if (_pathsWithListeners.add(module.getName() + ":" + path.toString()))
        {
            LOG.debug("registering a listener on: " + resource.toString());
            MergedDirectoryResource mdr = (MergedDirectoryResource) resource;
            mdr.registerListener(_watcher, getListener(module), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
    }


    private static class FileListenerResource extends ResourceWrapper
    {
        private final Module _module;
        private final ModuleResourceCache _cache;

        public FileListenerResource(Resource resource, Module module, ModuleResourceCache cache)
        {
            super(resource);
            _module = module;
            _cache = cache;
        }

        @Override
        public Collection<? extends Resource> list()
        {
            _cache.ensureListener(getWrappedResource(), _module);

            // Wrap each "collection" (directory) with a FileListenerResource to ensure they get listeners as well
            return super.list()
                .stream()
                .map(resource -> resource.isCollection() ? new FileListenerResource(resource, _module, _cache) : resource)
                .collect(Collectors.toList());
        }
    }


    private class StandardListener implements FileSystemDirectoryListener
    {
        private final Module _module;
        private final @Nullable FileSystemDirectoryListener _chainedListener;

        public StandardListener(Module module, @Nullable FileSystemDirectoryListener chainedListener)
        {
            _module = module;
            _chainedListener = chainedListener;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_module);

            if (null != _chainedListener)
                _chainedListener.entryCreated(directory, entry);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_module);

            if (null != _chainedListener)
                _chainedListener.entryDeleted(directory, entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_module);

            if (null != _chainedListener)
                _chainedListener.entryModified(directory, entry);
        }

        @Override
        public void overflow()
        {
            LOG.warn("Overflow!!");

            // I guess we should just clear the entire cache
            clear();

            if (null != _chainedListener)
                _chainedListener.overflow();
        }
    }
}
