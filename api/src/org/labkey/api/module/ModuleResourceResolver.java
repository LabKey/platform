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
package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.labkey.api.collections.Cache;
import org.labkey.api.collections.TTLCacheMap;
import org.labkey.api.resource.ClassResourceCollection;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: kevink
 * Date: Mar 13, 2010 10:39:31 AM
 */
public class ModuleResourceResolver implements Resolver
{
    static Logger _log = Logger.getLogger(ModuleResourceResolver.class);
    private static final TTLCacheMap<Pair<Resolver, Path>, Resource> _resources = new TTLCacheMap<Pair<Resolver, Path>, Resource>(1024, Cache.HOUR, "Module resources");

    Module _module;
    MergedDirectoryResource _root;
    ClassResourceCollection[] _classes;

    ModuleResourceResolver(Module module, List<File> dirs, Class... classes)
    {
        _module = module;

        List<ClassResourceCollection> additional = new ArrayList<ClassResourceCollection>(classes.length);
        for (Class clazz : classes)
            additional.add(new ClassResourceCollection(clazz));


        _root = new MergedDirectoryResource(this, Path.emptyPath, dirs);
        _classes = additional.toArray(new ClassResourceCollection[classes.length]);
    }

    public Path getRootPath()
    {
        return Path.emptyPath;
    }

    public Resource lookup(Path path)
    {
        if (path == null || !path.startsWith(getRootPath()))
            return null;

        Path normalized = path.normalize();
        Pair<Resolver, Path> cacheKey = new Pair<Resolver, Path>(this, normalized);
        Resource r = _resources.get(cacheKey);
        if (r == null)
        {
            r = resolve(normalized);
            if (null == r)
                return null;
            if (r.exists())
            {
                _log.debug(normalized + " -> " + r.getPath());
                _resources.put(cacheKey, r);
            }
        }
        return r;
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
}
