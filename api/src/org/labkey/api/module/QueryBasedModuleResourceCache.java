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
package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Path;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Cache for module resources that are associated with specific queries (as opposed to ModuleResourceCache, which supports a
 * single, consistent directory per module and PathBasedModuleResourceCache, which loads resources from arbitrary paths). For
 * performance, this class proactively loads all resources in the resource root of each module when any resource is requested
 * from that module. This class caches the loaded resources, registers file system listeners for each resource directory, and
 * clears the cache of all resources for a module in response to file system changes in that module.
 *
 * User: adam
 * Date: 12/26/13
 */
public final class QueryBasedModuleResourceCache<T>
{
    private static final Logger LOG = Logger.getLogger(QueryBasedModuleResourceCache.class);

    private final Path _root;
    private final BlockingCache<Module, Map<Path, Collection<T>>> _cache;
    private final QueryBasedModuleResourceCacheHandler<T> _handler;
    private final Set<String> _pathsWithListeners = new ConcurrentHashSet<>();
    private final FileSystemWatcher _watcher = FileSystemWatchers.get("Query-based module resource watcher");

    QueryBasedModuleResourceCache(Path root, String description, QueryBasedModuleResourceCacheHandler<T> handler)
    {
        _root = root;

        final CacheLoader<Path, Collection<T>> loader = handler.getResourceLoader();

        // Wrap the provided cache loader with a cache loader that recurses all resource directories and registers listeners
        // on first visit to each directory. This approach ensures we skip this bookkeeping when objects are simply retrieved
        // from the cache.
        CacheLoader<Module, Map<Path, Collection<T>>> wrapper = new CacheLoader<Module, Map<Path, Collection<T>>>()
        {
            @Override
            public Map<Path, Collection<T>> load(Module module, @Nullable Object argument)
            {
                Map<Path, Collection<T>> map = new HashMap<>();

                // Load from top-level <root> directory
                loadFromResourceRoot(module, module.getModuleResource(_root), map);

                // Need to load from "assay/*/<root>" as well
                Resource assayRoot = module.getModuleResource(AssayService.ASSAY_DIR_NAME);

                if (null != assayRoot && assayRoot.isCollection())
                {
                    assayRoot.list().stream()
                        .filter(Resource::isCollection)
                        .forEach(dir -> loadFromResourceRoot(module, dir.find(_root.getName()), map));
                }

                // Copy to a new HashMap to size the map appropriately
                return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(map));
            }

            private void loadFromResourceRoot(Module module, Resource rootResource, Map<Path, Collection<T>> map)
            {
                if (null != rootResource && rootResource.isCollection())
                    recurse(module, rootResource, map);
            }

            private void recurse(Module module, Resource dir, Map<Path, Collection<T>> map)
            {
                Path path = dir.getPath();

                // Register a listener, if this directory has never been visited before
                if (_pathsWithListeners.add(path.toString()))
                {
                    Resource parent = module.getModuleResource(path);

                    if (null != parent)
                    {
                        LOG.debug("registering a listener on: " + parent);
                        ((MergedDirectoryResource) parent).registerListener(_watcher, getListener(module), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    }
                }

                Collection<T> loadedResource = loader.load(path, module);

                if (null != loadedResource)
                    map.put(path, loadedResource);

                dir.list().stream()
                    .filter(Resource::isCollection)
                    .forEach(resource -> recurse(module, resource, map));
            }
        };

        _handler = handler;
        _cache = CacheManager.getBlockingCache(200, CacheManager.DAY, description, wrapper);  // Cache is one entry per module
    }

    @NotNull
    public Collection<T> getResource(Module module, Path path)
    {
        Map<Path, Collection<T>> map = _cache.get(module, null);
        Collection<T> collection = map.get(path);

        return null != collection ? collection : Collections.emptyList();
    }

    // Clear a single module's resources from the cache
    private void remove(Module module)
    {
        _cache.remove(module);
    }

    // Clear the whole cache
    private void clear()
    {
        _cache.clear();
    }

    private FileSystemDirectoryListener getListener(Module module)
    {
        return new StandardListener(module, _handler.createChainedDirectoryListener(module));
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
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                // Remove even on create, since we might have previously cached a miss
                remove(_module);

                if (null != _chainedListener)
                    _chainedListener.entryCreated(directory, entry);
            }
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                remove(_module);

                if (null != _chainedListener)
                    _chainedListener.entryDeleted(directory, entry);
            }
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                remove(_module);

                if (null != _chainedListener)
                    _chainedListener.entryModified(directory, entry);
            }
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
