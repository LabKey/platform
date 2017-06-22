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
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Standard cache for file-system resources provided by a module. An instance of this class manages one specific type of
 * resource (e.g., query, custom view, report, etc.) for all modules. This class loads, returns, and invalidates a single
 * object per module (referred to as a "resource map" below), typically a Map, MultiValuedMap, Collection, or a bean
 * with multiple Maps or Collections that offer different lookup options to callers. The cache uses ResourceRootProviders
 * to navigate the layout of resources (single directory, query-based hierarchy, arbitrary hierarchy, etc.) and uses a
 * FileListenerResource to ensure file system listeners are registered in every resource directory. The cache invalidates
 * a module's resource map whenever a file system change (update, delete, or add of any file or directory) occurs within
 * the corresponding resource directories of that module. A single change to a single file will therefore result in
 * reloading all the resources of the given type in that module.
 *
 * Note: Loading, caching, and invalidating all resources in each module together is a simple approach that provides good
 * performance and flexibility. It supports the "simple" model where all resources live in a single directory as well as
 * more complex models like query-based and path-based lookups, all via a single class and its helpers. It also easily
 * supports common retrieval patterns like getting a single resource, all resources, or some filtered subset of resources.
 * Previous approaches that attempted to cache and invalidate individual resources were much more complex and required
 * new cache classes for each retrieval model and resource layout.
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

    ModuleResourceCache(String description, ModuleResourceCacheHandler<V> handler, ResourceRootProvider provider, ResourceRootProvider... extraProviders)
    {
        CacheLoader<Module, V> wrapper = new CacheLoader<Module, V>()
        {
            @Override
            public V load(Module module, Object argument)
            {
                ModuleResourceCache<V> cache = (ModuleResourceCache<V>)argument;
                Resource resourceRoot = new FileListenerResource(module.getModuleResource(Path.rootPath), module, cache);
                Stream<Resource> resourceRoots = getResourceRoots(resourceRoot, provider, extraProviders);

                Stream<? extends Resource> resources = resourceRoots
                    .flatMap(root -> root.list().stream())
                    .filter(Resource::isFile);

                return handler.load(resources, module);
            }

            private @NotNull Stream<Resource> getResourceRoots(@NotNull Resource rootResource, ResourceRootProvider provider, ResourceRootProvider... extraProviders)
            {
                Collection<Resource> roots = new LinkedList<>();

                provider.fillResourceRoots(rootResource, roots);

                for (ResourceRootProvider extraProvider : extraProviders)
                    extraProvider.fillResourceRoots(rootResource, roots);

                return roots.isEmpty() ? Stream.empty() : roots.stream();
            }

            @Override
            public String toString()
            {
                return "CacheLoader for \"" + description + "\" (" + handler.getClass().getName() + ")";
            }
        };

        _cache = CacheManager.getBlockingCache(Constants.getMaxModules(), CacheManager.DAY, description, wrapper);  // Cache is one entry per module
        _handler = handler;
    }

    public @NotNull V getResourceMap(Module module)
    {
        return _cache.get(module, this);
    }

    /**
     *  Return a stream of all resource maps managed by this cache that are defined in all modules
     */
    public @NotNull Stream<V> streamAllResourceMaps()
    {
        return streamResourceMaps(ModuleLoader.getInstance().getModules());
    }

    /**
     *  Return a stream of all resource maps managed by this cache that are defined in the active modules
     *  in the specified Container.
     */
    public @NotNull Stream<V> streamResourceMaps(Container c)
    {
        return streamResourceMaps(c.getActiveModules());
    }

    /**
     *  Return a stream of all resource maps managed by this cache that are defined in the specified modules
     */
    public @NotNull Stream<V> streamResourceMaps(Collection<Module> modules)
    {
        return modules.stream().map(this::getResourceMap);
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
        public Collection<String> listNames()
        {
            ensureListener();

            return super.listNames();
        }

        @Override
        public Collection<? extends Resource> list()
        {
            ensureListener();

            // Wrap all child directories with a FileListenerResource to ensure they get listeners as well
            return super.list()
                .stream()
                .map(this::wrap)
                .collect(Collectors.toList());
        }

        @Override
        public Resource parent()
        {
            // Wrap parent with a FileListenerResource
            return wrap(super.parent());
        }

        @Override
        public Resource find(String name)
        {
            ensureListener();

            Resource resource = super.find(name);
            return null != resource ? wrap(super.find(name)) : null;
        }

        // Ensure that directory resources are FileListenerResources
        private Resource wrap(Resource resource)
        {
            return resource.isCollection() && !(resource instanceof FileListenerResource) ? new FileListenerResource(resource, _module, _cache) : resource;
        }

        // Ensure that a file listener associated with this cache is registered in this directory
        private void ensureListener()
        {
            if (isCollection())
                _cache.ensureListener(getWrappedResource(), _module);
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
