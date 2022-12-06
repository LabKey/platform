/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.SupportsFileSystemWatcher;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:37:56 AM
 */
public class DirectoryResource extends AbstractResourceCollection implements SupportsFileSystemWatcher
{
    private static final Cache<Pair<Resolver, Path>, Map<String, Resource>> CHILDREN_CACHE = CacheManager.getBlockingCache(5000, CacheManager.DAY, "Directory resources", null);

    private final File _dir;
    private final Pair<Resolver, Path> _cacheKey;

    private final CacheLoader<Pair<Resolver, Path>, Map<String, Resource>> _loader = new CacheLoader<>()
    {
        @Override
        public Map<String, Resource> load(@NotNull Pair<Resolver, Path> key, @Nullable Object argument)
        {
            Map<String, Resource> children = new CaseInsensitiveTreeMap<>();

            if (null != _dir && _dir.isDirectory())
            {
                File[] files = _dir.listFiles();
                if (files != null)
                {
                    for (File f : files)
                    {
                        String name = f.getName();
                        // Issue 11189: default custom view .qview.xml file is hidden on MacOX or Linux
                        if (!".qview.xml".equalsIgnoreCase(name) && f.isHidden())
                            continue;
//                        if (_resolver.filter(name))
//                            continue;

                        Path path = getPath().append(name);
                        Resource r = f.isFile() ?
                                new FileResource(path, f, _resolver) :
                                new DirectoryResource(_resolver, path, f);
                        Resource prev = children.put(name, r);
                        assert null == prev;
                    }
                }
            }

            return Collections.unmodifiableMap(children);
        }
    };

    // Static method that operates on the shared cache; removes all children associated with this resolver.
    public static void clearResourceCache(final Resolver resolver)
    {
        CHILDREN_CACHE.removeUsingFilter(key -> key.first == resolver);
    }

    public DirectoryResource(Resolver resolver, Path path, File dir)
    {
        super(path, resolver);
        _dir = dir;
        _cacheKey = new Pair<>(_resolver, getPath());
    }

    public Path getRelativePath(java.nio.file.Path nioPath)
    {
        return new Path(_dir.toPath().relativize(nioPath));
    }

    @Override
    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    private Map<String, Resource> getChildren()
    {
        return CHILDREN_CACHE.get(_cacheKey, null, _loader);
    }

    @Override
    public Collection<Resource> list()
    {
        Map<String, Resource> children = getChildren();
        return new ArrayList<>(children.values());
    }

    public void clearChildren()
    {
        CHILDREN_CACHE.remove(_cacheKey);
    }

    @Override
    public boolean exists()
    {
        return null != _dir && _dir.exists();
    }

    @Override
    public boolean isCollection()
    {
        return null != _dir && _dir.isDirectory();
    }

    @Override
    public Resource find(String name)
    {
        return getChildren().get(name);
    }

    @Override
    public Collection<String> listNames()
    {
        return new ArrayList<>(getChildren().keySet());
    }

    public File getDir()
    {
        return _dir;
    }

    // Listen for events in the directory associated with this resource
    @SafeVarargs
    @Override
    public final void registerListener(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
    {
        if (isCollection())
        {
            registerListener(_dir.toPath(), watcher, listener, events);
        }
    }

    @SafeVarargs
    @Override
    public final void registerListenerOnParent(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
    {
        registerListener(_dir.toPath().getParent(), watcher, listener, events);
    }

    @SafeVarargs
    private static void registerListener(java.nio.file.Path path, FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
    {
        try
        {
            watcher.addListener(path, listener, events);
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }
}
