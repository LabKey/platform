/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheMap;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.*;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:37:56 AM
 */
public class MergedDirectoryResource extends AbstractResourceCollection
{
    private static final CacheMap<Pair<Resolver, Path>, Map<String, Resource>> CHILDREN_CACHE = CacheManager.getCacheMap(50, "MergedDirectoryResourceCache");

    List<File> _dirs;
    Resource[] _additional;
    long versionStamp;
    long versionStampTime;
    long minCacheTime;

    final Object _lock = new Object();

    private class CaseInsensitiveTreeMap<V> extends TreeMap<String,V>
    {
        CaseInsensitiveTreeMap()
        {
            super(new Comparator<String>(){
                    public int compare(String s1, String s2)
                    {
                        return s1.compareToIgnoreCase(s2);
                    }
                });
        }
    }

    public MergedDirectoryResource(Resolver resolver, Path path, List<File> dirs, Resource... children)
    {
        super(path, resolver);
        _dirs = dirs;
        _additional = children;
    }

    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    Map<String, Resource> getChildren()
    {
        synchronized (_lock)
        {
            Pair<Resolver, Path> cacheKey = new Pair(_resolver, getPath());
            Map<String, Resource> children = CHILDREN_CACHE.get(cacheKey);
            if (null == children)
            {
                Map<String, ArrayList<File>> map = new CaseInsensitiveTreeMap<ArrayList<File>>();
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
                        if (f.isHidden())
                            continue;
//                        if (_resolver.filter(name))
//                            continue;
                        if (!map.containsKey(name))
                            map.put(name, new ArrayList<File>(Arrays.asList(f)));
                        else
                        {
                            // only merge directories together
                            ArrayList<File> existing = map.get(name);
                            if (existing.get(0).isDirectory() && f.isDirectory())
                                existing.add(f);
                        }
                    }
                }
                children = new CaseInsensitiveTreeMap<Resource>();
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

                CHILDREN_CACHE.put(cacheKey, children);
            }
            return children;
        }
    }

    public Collection<Resource> list()
    {
        Map<String, Resource> children = getChildren();
        return new ArrayList<Resource>(children.values());
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
        Resource r = getChildren().get(name);
        if (r == null && AppProps.getInstance().isDevMode())
        {
            for (File dir : _dirs)
            {
                // might not be case sensitive, but this is just devmode
                File f = new File(dir,name);
                if (f.exists())
                    return new FileResource(getPath().append(f.getName()), f, _resolver);
            }
        }
        return r;
    }

    public Collection<String> listNames()
    {
        return new ArrayList<String>(getChildren().keySet());
    }

    public long getVersionStamp()
    {
        synchronized (_lock)
        {
            if (System.currentTimeMillis() > versionStampTime + minCacheTime)
            {
                long version = getLastModified();

                for (Resource r : list())
                    version += r.getVersionStamp();

                versionStamp = version;
            }

            return versionStamp;
        }
    }

    public long getLastModified()
    {
        return exists() ? _dirs.get(0).lastModified() : Long.MIN_VALUE;
    }

}
