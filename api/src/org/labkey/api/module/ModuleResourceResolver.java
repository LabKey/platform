/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.ClassResourceCollection;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
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
    private static final Logger LOG = Logger.getLogger(ModuleResourceResolver.class);
    private static final StringKeyCache<Resource> RESOURCES = CacheManager.getStringKeyCache(4096, CacheManager.HOUR, "Module resources");
    private static final boolean DEV_MODE = AppProps.getInstance().isDevMode();

    private static final Resource CACHE_MISS = new AbstractResource(null, null) {
        public Resource parent()
        {
            return null;
        }
    };

    private final Module _module;
    private final MergedDirectoryResource _root;
    private final ClassResourceCollection[] _classes;

    ModuleResourceResolver(Module module, List<File> dirs, Class... classes)
    {
        List<ClassResourceCollection> additional = new ArrayList<>(classes.length);

        for (Class clazz : classes)
            additional.add(new ClassResourceCollection(clazz, this));

        _module = module;
        _root = new MergedDirectoryResource(this, Path.emptyPath, dirs);
        _classes = additional.toArray(new ClassResourceCollection[classes.length]);
    }

    private static Resource get(String cacheKey)
    {
        return RESOURCES.get(cacheKey);
    }

    private static void put(String cacheKey, Resource r)
    {
        RESOURCES.put(cacheKey, r);
    }

    private static void miss(String cacheKey)
    {
        // Cache misses in production mode for the default time period and
        // in dev mode for a short time (about the length of a request.)
        RESOURCES.put(cacheKey, CACHE_MISS, DEV_MODE ? (15*CacheManager.SECOND) : CacheManager.DEFAULT_TIMEOUT);
    }

    private static void remove(String cacheKey)
    {
        RESOURCES.remove(cacheKey);
    }

    // Clear all resources from the cache for just this module
    public void clear()
    {
        String prefix = this.toString();  // Remove all entries having a key that starts with this module name
        RESOURCES.removeUsingPrefix(prefix);
        MergedDirectoryResource.clearResourceCache(this);
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
        String cacheKey = this + ":" + normalized;
        Resource r = get(cacheKey);

        if (r == CACHE_MISS)
            return null;

        if (r == null)
        {
            r = resolve(normalized);

            if (null == r)
            {
                LOG.debug("missed resource: " + path);
                miss(cacheKey);
                return null;
            }

            if (r.exists())
            {
                LOG.debug("resolved resource: " + r + " -> " + normalized);
                put(cacheKey, r);
                return r;
            }
        }
        else if (DEV_MODE && !r.exists())
        {
            // remove cached resource and try again
            LOG.debug("removed resource: " + r);
            remove(cacheKey);
            return lookup(path);
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

    public String toString()
    {
        return _module.getName();
    }

    public Module getModule()
    {
        return _module;
    }
}
