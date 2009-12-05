/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.webdav;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 10:45:23 AM
 */
public class WebdavService
{
    WebdavResolver _resolver = null;
    CopyOnWriteArrayList<Provider> _providers = new CopyOnWriteArrayList<Provider>();

    final static WebdavService _instance = new WebdavService();

    public static WebdavService get()
    {
        return _instance;
    }

    // this is the resolver used to resolve http requests
    public void setResolver(WebdavResolver r)
    {
        _resolver = r;
    }
    

    public WebdavResolver getResolver()
    {
        return _resolver;
    }


    public Resource lookup(Path path)
    {
        return _resolver.lookup(path);
    }


    public Resource lookup(String path)
    {
        return _resolver.lookup(Path.parse(path));
    }


    /**
     * This is where the root of the container hierachy is rooted
     * in the webapp namespace.
     *
     * This used to really be the servletPath, before we started
     * serving the static web content and the dynamic web content
     * from the same servlet.
     * 
     * @return "_webdav"
     */
    public static String getServletPath()
    {
        return "_webdav";
    }


    /**
     * Only for static web content, alias a collection path to another location.
     *
     * For simplicity and speed this is very limited.
     * 1) from must not exist
     * 2) from.getParent() must exist
     * 3) only links within the static webdav tree are supported
     *
     * @param from
     * @param target
     */
    public void addLink(@NotNull Path from, Path target)
    {
        if (null == target || from.equals(target))
        {
            removeLink(from);
            return;
        }
        if (from.equals(Path.rootPath) || from.startsWith(target))
            throw new IllegalArgumentException(from.toString() + " --> " + target.toString());
        Resource r  = _resolver.lookup(from);
        Resource rParent = _resolver.lookup(from.getParent());
        if (null != r && !r.isCollection() || !rParent.exists())
            throw new IllegalArgumentException(from.toString());
        if (!(rParent instanceof AbstractResource))
            throw new IllegalArgumentException(from.toString());
        ((AbstractResource)rParent).createLink(from.getName(), target);
    }


    public void removeLink(Path from)
    {
        Resource rParent = _resolver.lookup(from.getParent());
        if (null == rParent || !rParent.isCollection())
            throw new IllegalArgumentException(from.toString());
        if (!(rParent instanceof AbstractResource))
            throw new IllegalArgumentException(from.toString());
        ((AbstractResource)rParent).removeLink(from.getName());
    }

    
    /*
     * interface for resources that are accessible through http:
     */

    
    public interface Provider
    {
        // currently addChildren is called only for web folders
        @Nullable
        Set<String> addChildren(@NotNull Resource target);
        Resource resolve(@NotNull Resource parent, @NotNull String name);
    }

    public void addProvider(Provider provider)
    {
        _providers.add(provider);
    }

    public List<Provider> getProviders()
    {
        return _providers;
    }
}
