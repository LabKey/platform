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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * User: adam
 * Date: 12/26/13
 * Time: 7:17 AM
 */

// Cache for file-system resources where the associated files can appear in multiple directories within a module
// (as opposed to ModuleResourceCache, which supports a single, consistent directory per module). This class loads
// individual resources as requested, caches the loaded resources, registers file system listeners for each resource
// directory, and clears elements from the cache in response to file system changes.
//
// Unlike ModuleResourceCache, which proactively registers all file listeners on first reference, this class registers
// listeners on demand (any time a resource is loaded from a new directory). As a result, there's no way to query for
// a complete list of resources or resource names.
public final class PathBasedModuleResourceCache<T>
{
    private static final Logger LOG = Logger.getLogger(PathBasedModuleResourceCache.class);

    private final BlockingStringKeyCache<T> _cache;
    private final ModuleResourceCacheHandler<Path, T> _handler;
    private final Set<String> _pathsWithListeners = new ConcurrentHashSet<>();
    private final FileSystemWatcher _watcher = FileSystemWatchers.get("Path-based module resource watcher");

    PathBasedModuleResourceCache(String description, ModuleResourceCacheHandler<Path, T> handler)
    {
        final CacheLoader<String, T> loader = handler.getResourceLoader();

        // Wrap the provided cache loader with a cache loader that registers listeners on new directories. This approach
        // ensures we skip this bookkeeping when objects are simply retrieved from the cache.
        CacheLoader<String, T> wrapper = new CacheLoader<String, T>()
        {
            @Override
            public T load(String key, @Nullable Object argument)
            {
                // Register a listener, if this directory has never been visited before

                ModuleResourceCache.CacheId cid = ModuleResourceCache.parseCacheKey(key);
                Module module = cid.getModule();
                Path path = Path.parse(cid.getName());
                Path parentPath = path.getParent();

                // Use cache key as the canonical name for paths that have listeners
                String canonicalName = ModuleResourceCache.createCacheKey(module, parentPath.toString());

                if (_pathsWithListeners.add(canonicalName))
                {
                    Resource parent = module.getModuleResource(parentPath);

                    if (null != parent)
                    {
                        LOG.debug("registering a listener on: " + parent);
                        ((MergedDirectoryResource) parent).registerListener(_watcher, getListener(module, parent.getPath()), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    }
                }

                // Now call the handler's load method
                return loader.load(key, argument);
            }
        };

        _handler = handler;
        _cache = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, description, wrapper);
    }


    @Nullable
    public T getResource(Module module, Path path, @Nullable Object argument)
    {
        String key = _handler.createCacheKey(module, path);
        return _cache.get(key, argument);
    }

    @Nullable
    public T getResource(Module module, Path path)
    {
        return getResource(module, path, null);
    }

    @Nullable
    public T getResource(Resource r)
    {
        return getResource(r, null);
    }

    @Nullable
    public T getResource(Resource r, @Nullable Object argument)
    {
        // This is hackery, but at least we're now explicit about it
        Resolver resolver = r.getResolver();
        assert resolver instanceof ModuleResourceResolver;
        Module module = ((ModuleResourceResolver) resolver).getModule();

        return getResource(module, r.getPath(), argument);
    }

    // Clear a single resource from the cache
    private void removeResource(Module module, Path path)
    {
        String key = _handler.createCacheKey(module, path);
        _cache.remove(key);
    }

    // Clear the whole cache
    private void clear()
    {
        _cache.clear();
    }

    private FileSystemDirectoryListener getListener(Module module, Path path)
    {
        return new StandardListener(module, path, _handler.createChainedDirectoryListener(module));
    }


    private class StandardListener implements FileSystemDirectoryListener
    {
        private final Module _module;
        private final Path _path;
        private final @Nullable FileSystemDirectoryListener _chainedListener;

        public StandardListener(Module module, Path path, @Nullable FileSystemDirectoryListener chainedListener)
        {
            _module = module;
            _path = path;
            _chainedListener = chainedListener;
        }

        private void remove(String filename)
        {
            String primaryFilename = _handler.getResourceName(_module, filename);
            Path path = _path.resolve(Path.parse(primaryFilename));
            removeResource(_module, path);
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            String filename = entry.toString();
            if (_handler.isResourceFile(filename))
            {
                // Remove even on create, since we might have previously cached a miss
                remove(filename);

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
                remove(filename);

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
                remove(filename);

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
