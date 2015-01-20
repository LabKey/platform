/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: matthewb
 * Date: Jan 7, 2009
 * Time: 3:27:03 PM
 */
public class ModuleStaticResolverImpl implements WebdavResolver
{
    private static ModuleStaticResolverImpl _instance = new ModuleStaticResolverImpl();

    static
    {
        ServiceRegistry.get().registerService(WebdavResolver.class, _instance);
    }

    private static final Logger _log = Logger.getLogger(ModuleStaticResolverImpl.class);

    /** System property name for an extra directory of static content */
    private static final String EXTRA_WEBAPP_DIR = "extrawebappdir";

    private ModuleStaticResolverImpl()
    {
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    final Object initLock = new Object();
    AtomicBoolean initialized = new AtomicBoolean(false);


    // DavController has a per request cache, but we want to aggressively cache static file resources
    // Do we really need the _allStaticFiles and CHILDREN_CACHE, it seems like we should be able to combine these
    StaticResource _root = null;
    Cache<Path, WebdavResource> _allStaticFiles = CacheManager.getCache(CacheManager.UNLIMITED, CacheManager.DAY, "webdav static files");


    public boolean requiresLogin()
    {
        return false;
    }

    public Path getRootPath()
    {
        return Path.emptyPath;
    }

    final static Path pathIndexHtml = new Path("index.html");
    
    public WebdavResource welcome()
    {
        return lookup(pathIndexHtml);
    }

    
    public WebdavResource lookup(Path path)
    {
        Path normalized = path.normalize();

        WebdavResource r = _allStaticFiles.get(normalized);
        if (r == null)
        {
            r = resolve(normalized);
            if (null == r)
                return null;
            if (r.exists())
            {
                if (null != r.getFile())
                    _log.debug(normalized + " -> " + r.getFile().getPath());
                else
                    _log.debug(normalized + " -> " + r.toString());
                if (r instanceof _PublicResource)
                    _allStaticFiles.put(normalized,r);
            }
        }
        return r;
    }


    private WebdavResource resolve(Path path)
    {
        if (!initialized.get())
            init();

        WebdavResource r = _root;
        for (int i=0 ; i<path.size() ; i++)
        {
            String p = path.get(i);
            if (null == p || p.equalsIgnoreCase("META-INF") || p.equalsIgnoreCase("WEB-INF") || p.startsWith("."))
                return null;
            r = r.find(p);
            if (null == r)
                return null;
            if (r instanceof SymbolicLink)
            {
                Path remainder = path.subpath(i+1,path.getNameCount());
                r = ((SymbolicLink)r).lookup(remainder);
                break;
            }
        }
        return r;
    }
        

    private void init()
    {
        synchronized (initLock)
        {
            if (initialized.get())
                return;

            ArrayList<File> roots = new ArrayList<>();

            // Support an additional extraWebapp directory with site-specific content. This lets users drop
            // in things like robots.txt without them being deleted at upgrade or when the server restarts.
            // Defaults to extraWebapp as a peer to the labkeyWebapp directory, but can be configured with the
            // -Dextrawebappdir=<PATH> system property.
            String extraWebappPath = System.getProperty(EXTRA_WEBAPP_DIR);
            File extraWebappDir;
            if (extraWebappPath == null)
            {
                extraWebappDir = new File(ModuleLoader.getInstance().getWebappDir().getParentFile(), "extraWebapp");
            }
            else
            {
                extraWebappDir = new File(extraWebappPath);
            }
            if (extraWebappDir.isDirectory())
                roots.add(extraWebappDir);

            // modules
            //  - add in reverse dependency order, allows modules to replace index.html for instance
            HashSet<String> seen = new HashSet<>();
            ArrayList<Module> modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            Collections.reverse(modules);
            for (Module m : modules)
            {
                for (File d :  m.getStaticFileDirectories())
                {
                    if (!d.isDirectory())
                        continue;
                    d = FileUtil.getAbsoluteCaseSensitiveFile(d);
                    if (seen.add(d.getPath()))
                        roots.add(d);
                }
            }

            // webapp
            roots.add(ModuleLoader.getInstance().getWebappDir());

            // This is so '_webdav' shows up as a child when someone does a propfind on '/'
            _root = new StaticResource(null, Path.emptyPath, roots, new SymbolicLink(WebdavResolverImpl.get().getRootPath(), WebdavResolverImpl.get()));
            initialized.set(true);
        }
    }

    @Override
    public String toString()
    {
        return "static";
    }

    private abstract class _PublicResource extends AbstractWebdavResource
    {
        protected _PublicResource(Path path)
        {
            super(path);
        }

        @Override
        public WebdavResolver getResolver()
        {
            return ModuleStaticResolverImpl.this;
        }

        @Override
        public boolean canRead(User user, boolean forRead)
        {
            return true;
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return true;
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return false;
        }

        @Override
        public boolean canDelete(User user, boolean forDelete)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return false;
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return false;
        }

        @Override
        public boolean shouldIndex()
        {
            return false;
        }
    }

    private static final Cache<Path, Map<String, WebdavResource>> CHILDREN_CACHE = CacheManager.getCache(1000, CacheManager.DAY, "StaticResourceCache");

    private class StaticResource extends _PublicResource
    {
        WebdavResource _parent;
        List<File> _files;
        WebdavResource[] _additional; // for _webdav

        final Object _lock = new Object();

        StaticResource(WebdavResource parent, Path path, List<File> files, WebdavResource... addl)
        {
            super(path);
            this._parent = parent;
            this._files = files;
            _additional = addl;
        }

        @Override
        public WebdavResource parent()
        {
            return null != _parent ? _parent : super.parent();
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            Path contextPath = null==context ? AppProps.getInstance().getParsedContextPath() : Path.parse(context.getContextPath());
            return contextPath.append(getPath()).encode();            
        }

        @Override
        public WebdavResolver getResolver()
        {
            return ModuleStaticResolverImpl.this;
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return true;
        }

        Map<String,WebdavResource> getChildren()
        {
            synchronized (_lock)
            {
                Map<String, WebdavResource> children = CHILDREN_CACHE.get(getPath());
                if (null == children)
                {
                    Map<String, ArrayList<File>> map = new CaseInsensitiveTreeMap<>();
                    for (File dir : _files)
                    {
                        if (!dir.isDirectory())
                            continue;
                        File[] files = dir.listFiles();
                        if (files == null)
                            continue;
                        for (File f : files)
                        {
                            String name = f.getName();
                            if (name.startsWith(".") || name.equals("WEB-INF") || name.equals("META-INF"))
                                continue;
                            if (!map.containsKey(name))
                                map.put(name, new ArrayList<File>());
                            map.get(name).add(f);
                        }
                    }
                    children = new CaseInsensitiveTreeMap<>();
                    for (Map.Entry<String,ArrayList<File>> e : map.entrySet())
                    {
                        Path path = getPath().append(e.getKey());
                        children.put(e.getKey(), new StaticResource(this, path, e.getValue()));
                    }
                    for (WebdavResource r : _additional)
                        children.put(r.getName(),r);

                    CHILDREN_CACHE.put(getPath(), children);
                }
                return children;
            }
        }


        @Override
        public void createLink(String name, Path target)
        {
            synchronized (_lock)
            {
                Map<String, WebdavResource> originalChildren = getChildren();
                if (null != originalChildren.get(name))
                    throw new IllegalArgumentException(name + " already exists");
                // _children is not synchronized so don't add put, create a new map
                Map<String,WebdavResource> children = new CaseInsensitiveTreeMap<>();
                children.putAll(originalChildren);
                children.put(name, new SymbolicLink(getPath().append(name), target));
                CHILDREN_CACHE.put(getPath(), children);
            }
        }


        @Override
        public void removeLink(String name)
        {
            synchronized (_lock)
            {
                Map<String, WebdavResource> originalChildren = getChildren();
                WebdavResource link = originalChildren.get(name);
                if (null == link)
                    return; // silent?
                if (!(link instanceof SymbolicLink))
                    throw new IllegalArgumentException(name + " is not a link");
                // _children is not synchronized so don't add put, create a new map
                Map<String,WebdavResource> children = new CaseInsensitiveTreeMap<>();
                children.putAll(originalChildren);
                children.remove(name);
                CHILDREN_CACHE.put(getPath(), children);
            }
            ModuleStaticResolverImpl.this._allStaticFiles.clear();
        }

        @Override
        public File getFile()
        {
            return exists() ? _files.get(0) : null;
        }

        public Collection<WebdavResource> list()
        {
            Map<String, WebdavResource> children = getChildren();
            return new ArrayList<>(children.values());
        }

        public boolean exists()
        {
            return _files != null && !_files.isEmpty();
        }

        public boolean isCollection()
        {
            return exists() && _files.get(0).isDirectory();
        }

        public WebdavResource find(String name)
        {

            WebdavResource r = getChildren().get(name);
            if (r == null && AppProps.getInstance().isDevMode())
            {
                for (File dir : _files)
                {
                    // might not be case sensitive, but this is just devmode
                    File f = new File(dir,name);
                    if (f.exists())
                        return new StaticResource(this, getPath().append(f.getName()), new ArrayList<>(Collections.singletonList(f)));
                }
            }
            return r;
        }

        public boolean isFile()
        {
            return exists() && _files.get(0).isFile();
        }

        public Collection<String> listNames()
        {
            return new ArrayList<>(getChildren().keySet());
        }

        public long getLastModified()
        {
            return exists() ? _files.get(0).lastModified() : Long.MIN_VALUE;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            assert isFile();
            if (isFile())
                return new FileInputStream(_files.get(0));
            return null;
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException(); 
        }

        public long getContentLength()
        {
            if (isFile())
                return _files.get(0).length();
            return 0;
        }

        // check every 5 seconds (always check in devMode)
        final long _checkInterval = AppProps.getInstance().isDevMode() ? -1 : TimeUnit.SECONDS.toMillis(5);
        long _etagUpdated = 0;


        @Override
        public synchronized String getETag(boolean force)
        {
            long current = HeartBeat.currentTimeMillis();
            if (null == _etag || force || _etagUpdated + _checkInterval < current)
            {
                _etagUpdated = current;
                _etag = "W/\"" + getLastModified() + "\"";
            }
            return _etag;
        }
    }


/*
    class ServletResource extends _PublicResource
    {
        URL _url;
        boolean _file;

        ServletResource(Path path)
        {
            super(path);
            _url = getResource(path);
        }

        @Override
        public WebdavResolver getResolver()
        {
            return ModuleStaticResolverImpl.this;
        }

        public boolean exists()
        {
            return _url != null;
        }

        public boolean isCollection()
        {
            return false;
        }

        public WebdavResource find(String name)
        {
            return null;
        }

        public boolean isFile()
        {
            return true;
        }

        public Collection<String> listNames()
        {
            return Collections.emptyList();
        }

        public Collection<WebdavResource> list()
        {
            return Collections.emptyList();
        }

        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            if (exists())
                return _url.openStream();
            return null;
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public long getContentLength() throws IOException
        {
            return _url.openConnection().getContentLength();
        }
    }
*/


    public class SymbolicLink extends AbstractWebdavResourceCollection
    {
        final Path _target;

        SymbolicLink(Path path, WebdavResolver resolver)
        {
            super(path, resolver);
            _target = null;
        }

        SymbolicLink(Path path, Path target)
        {
            super(path, (WebdavResolver)null);
            _target = target;
        }
        
        public WebdavResource lookup(Path relpath)
        {
            Path full = null==_target ? getPath().append(relpath) : _target.append(relpath);
            WebdavResolver resolver = null == getResolver() ? ModuleStaticResolverImpl.this : getResolver();
            return resolver.lookup(full);
        }

        public WebdavResource find(String name)
        {
            return lookup(new Path(name));
        }

        public boolean exists()
        {
            return true;
        }

        public Collection<String> listNames()
        {
            WebdavResource r = getResolver().lookup(getPath());
            if (null == r)
                return Collections.emptyList();
            return r.listNames();
        }
    }


    /*
    URL getResource(Path path)
    {
        ServletContext c = ViewServlet.getViewServletContext();
        if (c != null)
        {
            try
            {
                return c.getResource(path.toString());
            }
            catch (MalformedURLException x)
            {
                ;
            }
        }
        return null;
    }
    */


    public static class TestCase extends Assert
    {
        @Test
        public void testLinks()
        {
            // Don't want to modify public webdavService
            WebdavService s = new WebdavService();
            s.setResolver(new ModuleStaticResolverImpl());

            WebdavResource rIcons = s.lookup(new Path("_icons"));
            assertNotNull(rIcons);
            assertTrue(rIcons.isCollection());

            WebdavResource rIcon = s.lookup(new Path("_icons","folder.gif"));
            assertTrue(rIcon.isFile());
            
            WebdavResource rI = s.lookup(new Path("I"));
            if (null != rI && rI.exists())
                s.removeLink(new Path("I"));
            rI = s.lookup(new Path("I"));
            assertTrue(rI == null || !rI.exists());
            
            // This test depends on knowing some existing webapp directories
            s.addLink(new Path("I"), new Path("_icons"));
            rI = s.lookup(new Path("I"));
            assertNotNull(rI);
            assertTrue(rI.isCollection());

            rIcon = s.lookup(new Path("I","folder.gif"));
            assertTrue(rIcon.isFile());

            s.removeLink(new Path("I"));
            rIcon = s.lookup(new Path("I","folder.gif"));
            assertTrue(rIcon == null || !rIcon.exists());
        }
    }
}
