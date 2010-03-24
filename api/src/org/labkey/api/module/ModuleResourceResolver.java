package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.labkey.api.collections.Cache;
import org.labkey.api.resource.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: kevink
 * Date: Mar 13, 2010 10:39:31 AM
 */
public class ModuleResourceResolver implements Resolver
{
    static Logger _log = Logger.getLogger(ModuleResourceResolver.class);

    Cache _resources = new Cache(1024, Cache.HOUR, "Module resources");

    Module _module;
    MergedDirectoryResource _root;

    ModuleResourceResolver(Module module, List<File> dirs, Class... classes)
    {
        _module = module;

        List<ClassResourceCollection> additional = new ArrayList<ClassResourceCollection>(classes.length);
        for (Class clazz : classes)
            additional.add(new ClassResourceCollection(clazz));

        _root = new MergedDirectoryResource(this, Path.emptyPath, dirs, additional.toArray(new Resource[classes.length]));
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
        String cacheKey = normalized.toString();

        /*
        ResourceRef ref = lookupRef(cacheKey);
        if (ref == null || ref.isStale())
        {
            Resource r = resolve(normalized);
            if (null == r)
                return null;
            if (r.exists())
            {
                _log.debug(normalized + " -> " + r.getPath());
                ref = new ResourceRef(r);
                _resources.put(cacheKey, ref);
            }
        }
        return ref != null ? ref.getResource() : null;
        */

        Resource r = (Resource)_resources.get(cacheKey);
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

}
