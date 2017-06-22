/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.api.resource.ClassResourceCollection;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

/**
 * User: kevink
 * Date: Mar 13, 2010 10:39:31 AM
 */
public class ModuleResourceResolver implements Resolver
{
    private static final Logger LOG = Logger.getLogger(ModuleResourceResolver.class);
    private static final BlockingStringKeyCache<Resource> CACHE = CacheManager.getBlockingStringKeyCache(50000, CacheManager.DAY, "Module resources", null);
    private static final FileSystemWatcher WATCHER = FileSystemWatchers.get();

    // This ends up one per module; Consider: single static set to track all registered listeners?
    private final Set<Path> _pathsWithListeners = new ConcurrentHashSet<>();
    private final Module _module;
    private final MergedDirectoryResource _root;
    private final ClassResourceCollection[] _classes;
    private final CacheLoader<String, Resource> RESOURCE_LOADER = (key, argument) -> {
        Path normalized = (Path)argument;
        Resource r = resolve(normalized);

        // Register a listener on every directory we encounter
        if (null != r && r.exists() && r.isCollection())
            registerListener(r);

        if (null == r)
            LOG.debug("missed resource: " + key);
        else if (r.exists())
            LOG.debug("resolved resource: " + key + " -> " + r);

        return r;
    };

    ModuleResourceResolver(Module module, List<File> dirs, Class... classes)
    {
        List<ClassResourceCollection> additional = new ArrayList<>(classes.length);

        for (Class clazz : classes)
            additional.add(new ClassResourceCollection(clazz, this));

        _module = module;
        _root = new MergedDirectoryResource(this, Path.emptyPath, dirs);
        _classes = additional.toArray(new ClassResourceCollection[classes.length]);
    }

    // r exists and r is a collection
    private void registerListener(Resource r)
    {
        Path path = r.getPath();

        if (_pathsWithListeners.add(path))
        {
            LOG.debug("registering a listener on: " + r.toString());

            ((MergedDirectoryResource)r).registerListener(WATCHER, new ModuleResourceResolverListener(), ENTRY_CREATE, ENTRY_DELETE);
        }
    }

    private void remove(final String fullPath)
    {
        String moduleName = _module.getName();

        CACHE.removeUsingFilter(key ->
        {
            if (key.startsWith(moduleName))
            {
                String shortPath = key.substring(moduleName.length() + 1);
                return fullPath.endsWith(shortPath);
            }

            return false;
        });
    }

    // Clear all resources from the cache for just this module
    public void clear()
    {
        String prefix = _module.getName();  // Remove all entries having a key that starts with this module name
        CACHE.removeUsingPrefix(prefix);
        MergedDirectoryResource.clearResourceCache(this);
    }

    public Path getRootPath()
    {
        return Path.emptyPath;
    }

    @Nullable
    public Resource lookup(Path path)
    {
        if (path == null || !path.startsWith(getRootPath()))
            return null;

        Path normalized = path.normalize();
        String cacheKey = _module.getName() + ":" + normalized;

        return CACHE.get(cacheKey, normalized, RESOURCE_LOADER);
    }

    private Resource resolve(Path path)
    {
        Resource r = resolveFileResource(path);
        if (r != null)
            return r;

        return resolveClassResource(path);
    }

    private Resource resolveFileResource(Path path)
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

    private Resource resolveClassResource(Path path)
    {
        String p = path.toString();
        for (ClassResourceCollection rc : _classes)
        {
            Resource r = rc.find(p);
            if (r != null)
                return r;
        }

        return null;
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
        return _module.getName();
    }

    public Module getModule()
    {
        return _module;
    }

    public class ModuleResourceResolverListener implements FileSystemDirectoryListener
    {
        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            LOG.debug(entry + " created");
            remove(new Path(directory.resolve(entry)).toString());
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            LOG.debug(entry + " deleted");
            remove(new Path(directory.resolve(entry)).toString());
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
