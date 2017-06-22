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
package org.labkey.api.resource;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:37:56 AM
 */
public class MergedDirectoryResource extends AbstractResourceCollection
{
    private static final Cache<Pair<Resolver, Path>, Map<String, Resource>> CHILDREN_CACHE = CacheManager.getBlockingCache(5000, CacheManager.DAY, "MergedDirectoryResourceCache", null);
    private static final FileSystemWatcher WATCHER = FileSystemWatchers.get();
    private static final Set<Pair<Resolver, Path>> KEYS_WITH_LISTENERS = new ConcurrentHashSet<>();

    private final List<File> _dirs;
    private final Resource[] _additional;
    private final Pair<Resolver, Path> _cacheKey;

    private final CacheLoader<Pair<Resolver, Path>, Map<String, Resource>> _loader = new CacheLoader<Pair<Resolver, Path>, Map<String, Resource>>()
    {
        @Override
        public Map<String, Resource> load(Pair<Resolver, Path> key, @Nullable Object argument)
        {
            //org.labkey.api.module.ModuleResourceResolver._log.debug("merged dir: " + ((children == null) ? "null" : "stale") + " cache: " + this);
            Map<String, ArrayList<File>> map = new CaseInsensitiveTreeMap<>();

            for (File dir : _dirs)
            {
                if (!dir.isDirectory())
                    continue;
                File[] files = dir.listFiles();
                if (files == null)
                    continue;
                for (File f : files)
                {
                    String name = f.getName();
                    // Issue 11189: default custom view .qview.xml file is hidden on MacOX or Linux
                    if (!".qview.xml".equalsIgnoreCase(name) && f.isHidden())
                        continue;
//                        if (_resolver.filter(name))
//                            continue;
                    if (!map.containsKey(name))
                        map.put(name, new ArrayList<>(Arrays.asList(f)));
                    else
                    {
                        // only merge directories together
                        ArrayList<File> existing = map.get(name);
                        if (existing.get(0).isDirectory() && f.isDirectory())
                            existing.add(f);
                    }
                }
            }

            Map<String, Resource> children = new CaseInsensitiveTreeMap<>();

            for (Map.Entry<String, ArrayList<File>> e : map.entrySet())
            {
                Path path = getPath().append(e.getKey());
                ArrayList<File> files = e.getValue();
                Resource r = files.size() == 1 && files.get(0).isFile() ?
                        new FileResource(path, files.get(0), _resolver) :
                        new MergedDirectoryResource(_resolver, path, files);
                children.put(e.getKey(), r);
            }

            for (Resource r : _additional)
                children.put(r.getName(), r);

            return Collections.unmodifiableMap(children);
        }
    };

    // Static method that operates on the shared cache; removes all children associated with this resolver.
    public static void clearResourceCache(final Resolver resolver)
    {
        CHILDREN_CACHE.removeUsingFilter(key -> key.first == resolver);
    }

    public MergedDirectoryResource(Resolver resolver, Path path, List<File> dirs, Resource... children)
    {
        super(path, resolver);
        assert validateDirs(dirs);
        _dirs = dirs;
        _additional = children;
        _cacheKey = new Pair<>(_resolver, getPath());

        if (!KEYS_WITH_LISTENERS.contains(_cacheKey))
        {
            registerListener(WATCHER, new MergedDirectoryResourceListener(), ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            KEYS_WITH_LISTENERS.add(_cacheKey);
        }
    }

    private boolean validateDirs(List<File> dirs)
    {
        Set<File> files = new HashSet<>();

        for (File dir : dirs)
            if (!files.add(dir))
                assert false : dir.toString() + " was listed twice!";

        return true;
    }

    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    private Map<String, Resource> getChildren()
    {
        return CHILDREN_CACHE.get(_cacheKey, null, _loader);
    }

    public Collection<Resource> list()
    {
        Map<String, Resource> children = getChildren();
        return new ArrayList<>(children.values());
    }

    public void clearChildren()
    {
        CHILDREN_CACHE.remove(_cacheKey);
    }

    public boolean exists()
    {
        return _dirs != null && !_dirs.isEmpty();
    }

    public boolean isCollection()
    {
        return exists() && _dirs.get(0).isDirectory();
    }

    public Resource find(String name)
    {
        return getChildren().get(name);
    }

    public Collection<String> listNames()
    {
        return new ArrayList<>(getChildren().keySet());
    }

    public List<File> getContents()
    {
        return _dirs != null ? _dirs : Collections.emptyList();
    }

    private class MergedDirectoryResourceListener implements FileSystemDirectoryListener
    {
        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            clearChildren();
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            clearChildren();
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            clearChildren();
        }

        @Override
        public void overflow()
        {
            clearChildren();
        }
    }

    // Listen for events in all directories associated with this resource
    @SafeVarargs
    public final void registerListener(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
    {
        if (isCollection())
        {
            for (File dir : _dirs)
            {
                try
                {
                    watcher.addListener(dir.toPath(), listener, events);
                }
                catch (IOException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
    }
}
