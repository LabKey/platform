/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 10:45:23 AM
 */
public class WebdavService
{
    WebdavResolver _resolver = null;
    CopyOnWriteArrayList<Provider> _providers = new CopyOnWriteArrayList<>();
    private Set<String> _preGzippedExtensions = new CaseInsensitiveHashSet();

    private static final List<WebdavResolver> _rootResolvers = new CopyOnWriteArrayList<>();

    final static WebdavService _instance;
    static
    {
        _instance = new WebdavService();
        ServiceRegistry.get().registerService(WebdavService.class, _instance);
    }

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

    public List<WebdavResolver> getRootResolvers()
    {
        return _rootResolvers;
    }

    public List<WebdavResolver> getEnabledRootResolvers()
    {
        List<WebdavResolver> enabledProviders = new ArrayList<>();
        for (WebdavResolver provider : _rootResolvers)
            if (provider.isEnabled())
                enabledProviders.add(provider);

        return enabledProviders;
    }

    public void registerRootResolver(WebdavResolver webdavResolver)
    {
        _rootResolvers.add(webdavResolver);
    }

    @Nullable
    public WebdavResource lookup(Path path)
    {
        return _resolver.lookup(path);
    }


    public WebdavResource lookup(String path)
    {
        return _resolver.lookup(Path.parse(path));
    }


    /**
     * This is where the root of the container hierarchy is rooted
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

    final private static Path _path = Path.parse("/_webdav/");

    public static Path getPath()
    {
        return _path;
    }

    public WebdavResource lookupHref(String href) throws URISyntaxException
    {
        URLHelper u = new URLHelper(href);
        Path p = u.getParsedPath();
        Path contextPath = AppProps.getInstance().getParsedContextPath();
        if (p.startsWith(contextPath))
            p = p.subpath(contextPath.size(), p.size());
        return lookup(p);
    }

    public void addLink(@NotNull Path from, @NotNull Path target, String indexPage)
    {
        _resolver.addLink(from, target, indexPage);
    }


    public void removeLink(Path from)
    {
        _resolver.removeLink(from);
    }


    /*
     * interface for resources that are accessible through http:
     */
    public interface Provider
    {
        // currently addChildren is called only for web folders
        @Nullable
        Set<String> addChildren(@NotNull WebdavResource target);
        WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name);
    }

    public void addProvider(Provider provider)
    {
        _providers.add(provider);
    }

    public List<Provider> getProviders()
    {
        return _providers;
    }

    public WebdavResolver getRootResolver()
    {
        return ServiceRegistry.get(WebdavResolver.class);
    }

    /**
     * If an extension is registered, when a file of that extension is requested via webdav, the server will always
     * set ContentEncoding=gzip in the response.
     */
    public void registerPreGzippedExtensions(String extension)
    {
        _preGzippedExtensions.add(extension);
    }

    public Set<String> getPreGzippedExtensions()
    {
        return Collections.unmodifiableSet(_preGzippedExtensions);
    }
}
