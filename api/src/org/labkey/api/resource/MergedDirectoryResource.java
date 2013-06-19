/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Filter;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:37:56 AM
 */
public class MergedDirectoryResource extends AbstractResourceCollection
{
    private static final Cache<Pair<Resolver, Path>, Map<String, Resource>> CHILDREN_CACHE = CacheManager.getCache(500, CacheManager.DAY, "MergedDirectoryResourceCache");
    private static final long VERSION_STAMP_CACHE_TIME = AppProps.getInstance().isDevMode() ? (15*CacheManager.SECOND) : CacheManager.DEFAULT_TIMEOUT;

    private final List<File> _dirs;
    private final Resource[] _additional;
    private final Pair<Resolver, Path> _cacheKey;
    private final Object _lock = new Object();

    private long _versionStamp;
    private long _versionStampTime;


    // Static method that operates on the shared cache; removes all children associated with this resolver.
    public static void clearResourceCache(final Resolver resolver)
    {
        CHILDREN_CACHE.removeUsingFilter(new Filter<Pair<Resolver, Path>>()
        {
            @Override
            public boolean accept(Pair<Resolver, Path> key)
            {
                return key.first == resolver;
            }
        });
    }


    public MergedDirectoryResource(Resolver resolver, Path path, List<File> dirs, Resource... children)
    {
        super(path, resolver);
        _dirs = dirs;
        _additional = children;
        _cacheKey = new Pair<>(_resolver, getPath());
    }

    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    private Map<String, Resource> getChildren()
    {
        synchronized (_lock)
        {
            Map<String, Resource> children = CHILDREN_CACHE.get(_cacheKey);

            // Check isStale() first to establish the versionStamp the first time through.
            if (isStale() || null == children)
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

                children = new CaseInsensitiveTreeMap<>();

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

                CHILDREN_CACHE.put(_cacheKey, children);
            }

            return children;
        }
    }

    public Collection<Resource> list()
    {
        Map<String, Resource> children = getChildren();
        return new ArrayList<>(children.values());
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

    protected boolean isStale()
    {
        return _versionStamp != getVersionStamp();
    }

    public long getVersionStamp()
    {
        // To not hit the disk too often, wait a minimum amount of time before getting the lastModified time.
        if (_dirs != null && HeartBeat.currentTimeMillis() > _versionStampTime + VERSION_STAMP_CACHE_TIME)
        {
            //org.labkey.api.module.ModuleResourceResolver._log.debug("merged dir: checking timestamps: " + this);
            long version = 0;
            for (File d : _dirs)
                version += d.lastModified();

            _versionStampTime = HeartBeat.currentTimeMillis();
            _versionStamp = version;
        }

        return _versionStamp;
    }

    public long getLastModified()
    {
        return exists() ? _dirs.get(0).lastModified() : Long.MIN_VALUE;
    }
}
