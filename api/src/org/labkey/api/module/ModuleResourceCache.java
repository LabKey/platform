/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.ResourceWrapper;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ModuleChangeListener;
import org.labkey.api.util.Path;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
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
public final class ModuleResourceCache<V> implements ModuleChangeListener
{
    private static final Logger LOG = LogManager.getLogger(ModuleResourceCache.class);

    private final BlockingCache<String, CachePair> _cache;
    private final ModuleResourceCacheHandler<V> _handler;
    private final FileSystemWatcher _watcher = FileSystemWatchers.get();
    private final Set<String> _pathsWithListeners = new ConcurrentHashSet<>();
    private final CacheLoader<String, CachePair> _loader;

    ReferenceQueue<Module> q = new ReferenceQueue<>();
    class CachePair extends WeakReference<Module>
    {
        final String moduleName;
        final private V second;

        CachePair(Module m, V second)
        {
            super(m, q);
            this.moduleName = m.getName();
            this.second = second;
        }

        Module getModule()
        {
            return get();
        }
        V getValue()
        {
            return null == get() ? null : second;
        }
    }

    void processQueue()
    {
        if (null == q.poll()) // fast common case
            return;
        CachePair pair = null;
        try { pair = (CachePair) q.remove(1); } catch (InterruptedException x) { /* pass */}
        if (null == pair)
            return;
        // Unfortunately we don't know whether this Pair is still in the cache or not, and don't want to
        // kick out potentially newly loaded resources, so load new resources instead to kick out this entry
        Module m = ModuleLoader.getInstance().getModule(pair.moduleName);
        if (null != m)
            m.getModuleResource("/");
        else
            _cache.remove(pair.moduleName);
    }


    @Override
    public void onModuleChanged(Module module)
    {
        if (null != module)
            getListener(module).moduleChanged(module);
        processQueue();
    }


    ModuleResourceCache(String description, ModuleResourceCacheHandler<V> handler, ResourceRootProvider provider, ResourceRootProvider... extraProviders)
    {
        _loader = new CacheLoader<>()
        {
            @Override
            public CachePair load(@NotNull String moduleName, Object argument)
            {
                @SuppressWarnings("unchecked")
                Module module = (Module)argument;
                ModuleResourceCache<V> cache = ModuleResourceCache.this;
                Resource resourceRoot = new FileListenerResource(module.getModuleResource(Path.rootPath), module, cache);
                Stream<Resource> resourceRoots = getResourceRoots(resourceRoot, provider, extraProviders);

                Stream<? extends Resource> resources = resourceRoots
                    .flatMap(root -> root.list().stream())
                    .filter(Resource::isFile);

                return new CachePair(module, handler.load(resources,module));
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

        _cache = CacheManager.getBlockingCache(Constants.getMaxModules(), CacheManager.DAY, description, _loader);  // Cache is one entry per module
        _handler = handler;

        ContextListener.addModuleChangeListener(this);
    }

    private @NotNull V cacheGet(Module module)
    {
        processQueue();

        CachePair ret = _cache.get(module.getName(), module);

        // handle common case first
        Module cachedModule = ret.getModule();
        V cachedValue = ret.getValue();
        if (cachedModule == module)
            return cachedValue;

        // remove stale entries from cache (should be handled by listeners, but we're here so check again)
        Module current = ModuleLoader.getInstance().getModule(module.getName());
        if (cachedModule != current)
            _cache.remove(module.getName());

        // We sometimes load resources for the non-current module.  Don't use cache in that case.
        // This can happen while a module is being loaded but before the module is "registered" in the ModuleLoader.
        if (module == current)
            ret = _cache.get(module.getName(), module);
        else
            ret = _loader.load(module.getName(), module);

        return ret.second;
    }

    public @NotNull V getResourceMap(Module module)
    {
        return cacheGet(module);
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
       _cache.remove(module.getName());
        module.getModuleResource("/");
    }

    private void removeResourceMap(String moduleName)
    {
        _cache.remove(moduleName);
        Module module = ModuleLoader.getInstance().getModule(moduleName);
        if (null != module)
            module.getModuleResource("/");
    }

    // Clear the whole cache
    private void clear()
    {
        _cache.clear();
    }

    ModuleResourceCacheListener getListener(Module module)
    {
        return new StandardListener(module, _handler.createChainedListener(module));
    }

    public void ensureListener(Resource resource, Module module)
    {
        assert resource.isCollection();
        DirectoryResource mdr = (DirectoryResource) resource;

        if (_pathsWithListeners.add(getPathsWithListenersKey(module, mdr.getDir().toPath())))
        {
            LOG.debug("registering a listener on: " + resource.toString());
            mdr.registerListener(_watcher, getListener(module), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
    }

    private String getPathsWithListenersKey(Module module, java.nio.file.Path path)
    {
        return module.getName() + ":" + path.toString();
    }

    private String getPathsWithListenersKey(String moduleName, java.nio.file.Path path)
    {
        return moduleName + ":" + path.toString();
    }

    private static class FileListenerResource extends ResourceWrapper
    {
        private final Module _module;
        private final ModuleResourceCache<?> _cache;

        public FileListenerResource(Resource resource, Module module, ModuleResourceCache<?> cache)
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
        public Resource find(Path.Part name)
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


    private class StandardListener implements ModuleResourceCacheListener
    {
        private final String _moduleName;
        private final @Nullable ModuleResourceCacheListener _chainedListener;

        public StandardListener(Module module, @Nullable ModuleResourceCacheListener chainedListener)
        {
            _moduleName = module.getName();
            _chainedListener = chainedListener;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_moduleName);

            if (null != _chainedListener)
                _chainedListener.entryCreated(directory, entry);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_moduleName);

            if (null != _chainedListener)
                _chainedListener.entryDeleted(directory, entry);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeResourceMap(_moduleName);

            if (null != _chainedListener)
                _chainedListener.entryModified(directory, entry);
        }

        @Override
        public void directoryDeleted(java.nio.file.Path directory)
        {
            _pathsWithListeners.remove(getPathsWithListenersKey(_moduleName, directory));
            removeResourceMap(_moduleName);

            Module module = ModuleLoader.getInstance().getModule(_moduleName);
            if (null != module)
                moduleChanged(module);

            if (null != _chainedListener)
                _chainedListener.directoryDeleted(directory);
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

        @Override
        public void moduleChanged(Module module)
        {
            removeResourceMap(module);

            if (null != _chainedListener)
                _chainedListener.moduleChanged(module);
        }

        @Override
        public String toString()
        {
            return super.toString() + " - " + _moduleName;
        }
    }
}
