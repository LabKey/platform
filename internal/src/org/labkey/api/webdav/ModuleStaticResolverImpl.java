/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheListener;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.SupportsFileSystemWatcher;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.ModuleChangeListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
public class ModuleStaticResolverImpl implements WebdavResolver, ModuleChangeListener, CacheListener
{
    private static final ModuleStaticResolverImpl _instance = new ModuleStaticResolverImpl();

    static
    {
        ServiceRegistry.get().registerService(WebdavResolver.class, _instance);
    }

    private static final Logger _log = LogManager.getLogger(ModuleStaticResolverImpl.class);

    /** System property name for an extra directory of static content */
    private static final String EXTRA_WEBAPP_DIR = "extrawebappdir";

    private ModuleStaticResolverImpl()
    {
        ContextListener.addModuleChangeListener(this);
        CacheManager.addListener(this);
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
    Cache<Path, WebdavResource> _allStaticFiles = CacheManager.getCache(CacheManager.UNLIMITED, CacheManager.DAY, "WebDAV static files");


    @Override
    public void onModuleChanged(Module m)
    {
        clearCaches();
    }

    @Override
    public void clearCaches()
    {
        _allStaticFiles.clear();
        CHILDREN_CACHE.clear();
        initialized.set(false);
    }

    @Override
    public boolean requiresLogin()
    {
        return false;
    }

    @Override
    public Path getRootPath()
    {
        return Path.emptyPath;
    }

    final static Path pathIndexHtml = new Path("index.html");

    @Override
    public boolean allowHtmlListing()
    {
        // must return false or we won't call welcome()
        return false;
    }

    @Override
    public WebdavResource welcome()
    {
        return lookup(pathIndexHtml);
    }

    @Override
    public LookupResult lookupEx(Path path)
    {
        Path normalized = path.normalize();
        if (null == normalized)
            return null;

        WebdavResource r = _allStaticFiles.get(normalized);
        if (null != r)
            return new LookupResult(this, r);

        LookupResult result = resolve(normalized);
        if (null == result || null == result.resource)
            return null;
        r = result.resource;
        if (r.exists())
        {
            if (null != r.getFile())
                _log.debug(normalized + " -> " + r.getFile().getPath());
            else
                _log.debug(normalized + " -> " + r.toString());
            if (r instanceof _PublicResource)
                _allStaticFiles.put(normalized,r);
        }
        return result;
    }


    private LookupResult resolve(Path path)
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
                LookupResult result = ((SymbolicLink)r).lookupEx(remainder);
                return result;
            }
        }
        return new LookupResult(this,r);
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

            List<WebdavResource> webdavResources = new ArrayList<>();
            WebdavService.get().getRootResolvers().forEach(webdavResolver ->
                webdavResources.add(new SymbolicLink(webdavResolver.getRootPath(), webdavResolver))
            );
            _root = new StaticResource(null, Path.emptyPath, roots, webdavResources);
            initialized.set(true);
        }
    }


    // Parent -> map(name->shortcut,index)
    private final Map<Path, Map<String, Pair<Path, String>>> shortcuts = new HashMap<>();


    @NotNull
    public Map<String, Pair<Path, String>> getShortcuts(Path collection)
    {
        synchronized (shortcuts)
        {
            HashMap<String, Pair<Path, String>> ret = new HashMap<>();
            Map<String, Pair<Path, String>> map = shortcuts.get(collection);
            if (null != map)
                ret.putAll(map);
            return ret;
        }
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
    @Override
    public void addLink(@NotNull Path from, @NotNull Path target, String indexPage)
    {
        if (null == target || from.equals(target))
        {
            removeLink(from);
            return;
        }
        if (from.equals(Path.rootPath) || from.startsWith(target))
            throw new IllegalArgumentException(from.toString() + " --> " + target.toString());

        WebdavResource rParent = lookup(from.getParent());
        if (!rParent.isCollection())
            throw new IllegalArgumentException(from.toString());
        if (!(rParent instanceof AbstractWebdavResource))
            throw new IllegalArgumentException(from.toString());

        synchronized (shortcuts)
        {
            Map<String,Pair<Path,String>> map = shortcuts.get(rParent.getPath());
            if (null == map)
            {
                map = new HashMap<>();
                shortcuts.put(rParent.getPath(), map);
            }
            map.put(from.getName(), new Pair<>(target,indexPage));
        }

        ((AbstractWebdavResource)rParent).createLink(from.getName(), target, indexPage);
    }


    @Override
    public void removeLink(Path from)
    {
        WebdavResource rParent = lookup(from.getParent());
        if (null == rParent || !rParent.isCollection())
            throw new IllegalArgumentException(from.toString());
        if (!(rParent instanceof AbstractWebdavResource))
            throw new IllegalArgumentException(from.toString());

        synchronized (shortcuts)
        {
            Map<String,Pair<Path,String>> map = shortcuts.get(rParent.getPath());
            if (null != map)
                map.remove(from.getName());
        }

        ((AbstractWebdavResource)rParent).removeLink(from.getName());
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
        public boolean canDelete(User user, boolean forDelete, List<String> message)
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

    private static final Cache<Path, Map<String, WebdavResource>> CHILDREN_CACHE = CacheManager.getCache(1000, CacheManager.DAY, "Static resources");

    private class StaticResource extends _PublicResource implements SupportsFileSystemWatcher
    {
        WebdavResource _parent;
        List<File> _files;
        List<WebdavResource> _additional; // for _webdav

        final Object _lock = new Object();

        StaticResource(WebdavResource parent, Path path, List<File> files, List<WebdavResource> addl)
        {
            super(path);
            _parent = parent;
            _files = files;
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
            return PageFlowUtil.staticResourceUrl(getPath().encode());
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
                                map.put(name, new ArrayList<>());
                            map.get(name).add(f);
                        }
                    }
                    children = new CaseInsensitiveTreeMap<>();
                    for (Map.Entry<String,ArrayList<File>> e : map.entrySet())
                    {
                        Path path = getPath().append(e.getKey());
                        children.put(e.getKey(), new StaticResource(this, path, e.getValue(), null));
                    }

                    if (_additional != null)
                    {
                        for (WebdavResource r : _additional)
                            children.put(r.getName(),r);
                    }

                    Map<String,Pair<Path,String>> shortcuts = getShortcuts(getPath());
                    for (Map.Entry<String,Pair<Path,String>> e : shortcuts.entrySet())
                    {
                        String name = e.getKey();
                        Path target = e.getValue().getKey();
                        String indexPage = e.getValue().getValue();

                        if (!children.containsKey(name))
                            children.put(name, new SymbolicLink(getPath().append(name), target, true, indexPage));
                    }

                    CHILDREN_CACHE.put(getPath(), Collections.unmodifiableMap(children));
                }
                return children;
            }
        }


        // just clear cache and let getChildren() create new children list with new shortcut
        @Override
        public void createLink(String name, Path target, String indexPage)
        {
            CHILDREN_CACHE.remove(getPath());
        }


        @Override
        public void removeLink(String name)
        {
            CHILDREN_CACHE.remove(getPath());
            ModuleStaticResolverImpl.this._allStaticFiles.clear();
        }


        @Override
        public File getFile()
        {
            return exists() ? _files.get(0) : null;
        }

        @Override
        public Collection<WebdavResource> list()
        {
            Map<String, WebdavResource> children = getChildren();
            return new ArrayList<>(children.values());
        }

        @Override
        public boolean exists()
        {
            return _files != null && !_files.isEmpty();
        }

        @Override
        public boolean isCollection()
        {
            return exists() && _files.get(0).isDirectory();
        }

        @Override
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
                        return new StaticResource(this, getPath().append(f.getName()), new ArrayList<>(Collections.singletonList(f)), null);
                }
            }
            return r;
        }

        @Override
        public boolean isFile()
        {
            return exists() && _files.get(0).isFile();
        }

        @Override
        public Collection<String> listNames()
        {
            return new ArrayList<>(getChildren().keySet());
        }

        @Override
        public long getLastModified()
        {
            return exists() ? _files.get(0).lastModified() : Long.MIN_VALUE;
        }

        @Override
        public InputStream getInputStream(User user) throws IOException
        {
            if (isFile())
                return new FileInputStream(_files.get(0));
            return null;
        }

        @Override
        public long copyFrom(User user, FileStream in)
        {
            throw new UnsupportedOperationException(); 
        }

        @Override
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

        // Listen for events in all directories associated with this resource
        @SafeVarargs
        @Override
        public final void registerListener(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
        {
            if (isCollection())
                registerListener(getFile(), watcher, listener, events);
        }

        @SafeVarargs
        @Override
        public final void registerListenerOnParent(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
        {
            if (null != getFile())
                registerListener(getFile().getParentFile(), watcher, listener, events);
        }

        @SafeVarargs
        private void registerListener(File dir, FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events)
        {
            if (null != dir)
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


    public class SymbolicLink extends AbstractWebdavResourceCollection implements WebdavResolver
    {
        final Path _target;
        final String _indexPage;
        final boolean _readOnly;

        SymbolicLink(Path path, WebdavResolver resolver)
        {
            super(path, resolver);
            _target = null;
            _indexPage = null;
            _readOnly = false;
        }

        SymbolicLink(Path path, Path target, boolean ro, @Nullable String indexPage)
        {
            super(path, (WebdavResolver)null);
            _target = target;
            _indexPage = indexPage;
            _readOnly = ro;
        }

        @Override
        public WebdavResource find(String name)
        {
            LookupResult res = lookupEx(new Path(name));
            return null == res ? null : res.resource;
        }

        @Override
        public boolean exists()
        {
            return true;
        }

        @Override
        public Collection<String> listNames()
        {
            WebdavResource r = getResolver().lookup(getPath());
            if (null == r)
                return Collections.emptyList();
            return r.listNames();
        }

        //
        // Resolver methods
        //

        @Override
        public boolean requiresLogin()
        {
            return false;
        }

        @Override
        public Path getRootPath()
        {
            return getPath();
        }

        @Override
        public LookupResult lookupEx(Path relpath)
        {
            Path full = null==_target ? getPath().append(relpath) : _target.append(relpath);
            WebdavResolver innerResolver = null == getResolver() ? ModuleStaticResolverImpl.this : getResolver();
            if (!innerResolver.isEnabled())
                return null;
            LookupResult inner = innerResolver.lookupEx(full);
            WebdavResource r = inner.resource;
            if (_readOnly)
            {
                if (null == inner.resource)
                    r = new WebdavResourceReadOnly(new WebdavResolverImpl.UnboundResource(full));
                else
                    r = new WebdavResourceReadOnly(r);
            }
            return new LookupResult(this, r);
        }

        @Override
        public boolean allowHtmlListing()
        {
            // for now just overload meaning of null==_indexPage
            return null == _indexPage;
        }

        @Override
        public WebdavResource welcome()
        {
            return find(defaultWelcomePage());
        }

        @Nullable
        @Override
        public String defaultWelcomePage()
        {
            if (null != _indexPage)
                return _indexPage;
            return "index.html";
        }

        @Override
        public String toString()
        {
            return super.toString();
        }
    }


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
            s.addLink(new Path("I"), new Path("_icons"), null);
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
