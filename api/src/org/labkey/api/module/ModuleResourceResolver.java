/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.resource.DirectoryResource;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Path;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

/**
 * User: kevink
 * Date: Mar 13, 2010 10:39:31 AM
 */
public class ModuleResourceResolver implements Resolver
{
    private static final Logger LOG = LogManager.getLogger(ModuleResourceResolver.class);
    private static final BlockingCache<String, Resource> CACHE = CacheManager.getBlockingStringKeyCache(50000, CacheManager.DAY, "Module resources", null);
    private static final FileSystemWatcher WATCHER = FileSystemWatchers.get();

    // This ends up one per module; Consider: single static set to track all registered listeners?
    private final Set<Path> _pathsWithListeners = new ConcurrentHashSet<>();
    private final String _moduleName;
    private final DirectoryResource _root;
    private final CacheLoader<String, Resource> RESOURCE_LOADER = (key, argument) -> {
        Path normalized = (Path)argument;
        Resource r = resolve(normalized);

        // Ensure listeners are registered on this resource and its ancestors
        if (null != r)
            ensureListeners(r);

        if (null == r)
            LOG.debug("missed resource: " + key);
        else if (r.exists())
            LOG.debug("resolved resource: " + key + " -> " + r);

        return r;
    };

    static
    {
        // Need to clear resource caches when modules change. See #40250
        ContextListener.addModuleChangeListener(m -> m.getModuleResolver().clear());
    }

    ModuleResourceResolver(Module module, File dir)
    {
        _moduleName = module.getName();
        _root = new DirectoryResource(this, Path.emptyPath, dir);
    }

    // Ensure listeners are registered on this resource (if it's a directory) and the resource's parent, grandparent, etc.
    private void ensureListeners(Resource r)
    {
        while (null != r)
        {
            if (r.exists() && r.isCollection())
            {
                Path path = r.getPath();

                if (_pathsWithListeners.add(path))
                {
                    LOG.debug("registering a listener on: " + r.toString());

                    ((DirectoryResource) r).registerListener(WATCHER, new ModuleResourceResolverListener(), ENTRY_CREATE, ENTRY_DELETE);
                }
                else
                {
                    LOG.debug("NOT registering a listener on: " + r.toString());
                    // Short-circuit -- if a path is registered then we know its ancestors are registered, so no need to keep looping
                    return;
                }
            }

            // Walk up the ancestors, regardless of whether a resource exists or not (its parent or grandparent might)
            r = r.parent();
        }
    }

    // Clear all resources from the cache for just this module
    public void clear()
    {
        // Remove all entries having a key that starts with this module name
        CACHE.removeUsingFilter(new Cache.StringPrefixFilter(_moduleName));
        DirectoryResource.clearResourceCache(this);
    }

    @Override
    public Path getRootPath()
    {
        return Path.emptyPath;
    }

    @Override
    @Nullable
    public Resource lookup(Path path)
    {
        if (path == null || !path.startsWith(getRootPath()))
            return null;

        Path normalized = path.normalize();
        String cacheKey = _moduleName + ":" + normalized;

        return CACHE.get(cacheKey, normalized, RESOURCE_LOADER);
    }

    private @Nullable Resource resolve(Path path)
    {
        Resource r = _root;
        for (int i=0 ; i<path.size() ; i++)
        {
            String p = path.get(i);
            if (null == p || filter(p))
                return null;
            r = r.find(p);
            if (null == r)
                return null;
        }

        return r;
    }

    protected boolean filter(String p)
    {
        return //p.equalsIgnoreCase("META-INF") ||
            p.equalsIgnoreCase("WEB-INF") ||
            p.equals("web") ||
            p.equals("webapp") ||
            p.startsWith(".");
    }

    public String toString()
    {
        return _moduleName;
    }

    public Module getModule()
    {
        return ModuleLoader.getInstance().getModule(_moduleName);
    }

    private class ModuleResourceResolverListener implements FileSystemDirectoryListener
    {
        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            LOG.debug(entry + " created");
            java.nio.file.Path nioPath = directory.resolve(entry);
            if (Files.isDirectory(nioPath))
                ensureListeners(resolve(_root.getRelativePath(nioPath)));
            clear(); // Clear all resources and children in this module. A bit heavy-handed, but attempts at targeted approaches have been wrong.
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            LOG.debug(entry + " deleted");
            java.nio.file.Path nioPath = directory.resolve(entry);
            if (Files.isDirectory(nioPath))
                _pathsWithListeners.remove(_root.getRelativePath(nioPath));
            clear(); // Clear all resources and children in this module. A bit heavy-handed, but attempts at targeted approaches have been wrong.
        }

        @Override
        public void directoryDeleted(java.nio.file.Path directory)
        {
            LOG.debug("Directory " + directory + " deleted");
            _pathsWithListeners.remove(_root.getRelativePath(directory));
            clear(); // Clear all resources and children in this module. A bit heavy-handed, but attempts at targeted approaches have been wrong.
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            throw new IllegalStateException("Modified shouldn't be called!");
        }

        @Override
        public void overflow()
        {
            LOG.debug("overflow!");
            CACHE.clear();    // Clear the entire cache
        }
    }
}
