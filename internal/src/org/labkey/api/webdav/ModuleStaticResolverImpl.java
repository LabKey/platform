/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.settings.AppProps;
import org.apache.log4j.Logger;
import org.apache.log4j.Category;

import javax.servlet.ServletContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 7, 2009
 * Time: 3:27:03 PM
 */
public class ModuleStaticResolverImpl implements WebdavResolver
{
    static ModuleStaticResolverImpl _instance = new ModuleStaticResolverImpl();
    static Category _log = Logger.getInstance(ModuleStaticResolverImpl.class);

    private ModuleStaticResolverImpl()
    {
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    final Object initLock = new Object();
    AtomicBoolean initialized = new AtomicBoolean(false);

    StaticResource _root = null;
    Map<String, Resource> _files = Collections.synchronizedMap(new HashMap<String,Resource>());


    public boolean requiresLogin()
    {
        return false;
    }

    public Resource lookup(String path)
    {
        String normalized = FileUtil.normalize(path).toLowerCase();
        Resource r = _files.get(normalized);
        if (r == null)
        {
            r = resolve(normalized);
            if (null == r)
                return null;
//            if (null == r)
//                r = new ServletResource(normalized);
            if (r.exists())
            {
                if (r instanceof StaticResource)
                    _log.debug(normalized + " -> " + ((StaticResource)r)._files.get(0).getPath());
                else
                    _log.debug(normalized + " -> {webapp}");
                _files.put(normalized,r);
            }
        }

        boolean directory = path.endsWith("/") || path.endsWith("/.") || path.endsWith("/..");
        if (!directory || r.isCollection())
            return r;
        return null;
    }


    private Resource resolve(String path)
    {
        if (!initialized.get())
            init();

        ArrayList<String> parts = FileUtil.normalizeSplit(path);

        Resource r = _root;
        for (String p : parts)
        {
            if (null == p || p.equalsIgnoreCase("META-INF") || p.equalsIgnoreCase("WEB-INF") || p.startsWith("."))
                return null;
            r = r.find(p);
            if (null == r)
                return null;
        }
        return r;
    }
        

    private void init()
    {
        synchronized (initLock)
        {
            if (initialized.get())
                return;
            
            HashSet<String> seen = new HashSet<String>();
            ArrayList<File> roots = new ArrayList<File>();
            
            for (Module m : ModuleLoader.getInstance().getModules())
            {
                for (File d :  m.getStaticFileDirectories())
                {
                    if (!d.isDirectory())
                        continue;
                    d = FileUtil.canonicalFile(d);
                    if (seen.add(d.getPath()))
                        roots.add(d);
                }
            }
            roots.add(ModuleLoader.getInstance().getWebappDir());

            _root = new StaticResource("/", roots);
            initialized.set(true);
        }
    }


    private abstract class _PublicResource extends AbstractResource
    {
        protected _PublicResource(String path)
        {
            super(path);
        }

        @Override
        public boolean canRead(User user)
        {
            return true;
        }

        @Override
        public boolean canList(User user)
        {
            return false;
        }

        @Override
        public boolean canRename(User user)
        {
            return false;
        }

        @Override
        public boolean canDelete(User user)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user)
        {
            return false;
        }

        @Override
        public boolean canWrite(User user)
        {
            return false;
        }
    }

    private class StaticResource extends _PublicResource
    {
        List<File> _files;
        AtomicReference<Map<String, Resource>> _children = new AtomicReference<Map<String, Resource>>();

        StaticResource(String path, List<File> files)
        {
            super(path);
            this._files = files;
        }

        public List<Resource> list()
        {
            Map<String, Resource> children = _children.get();

            if (null == children)
            {
                CaseInsensitiveHashMap<ArrayList<File>> map = new CaseInsensitiveHashMap<ArrayList<File>>();
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
                children = new CaseInsensitiveHashMap<Resource>();
                for (Map.Entry<String,ArrayList<File>> e : map.entrySet())
                {
                    String path = WebdavResolverImpl.c(getPath(), e.getKey());
                    children.put(e.getKey(), new StaticResource(path, e.getValue()));
                }
                _children.compareAndSet(null, children);
                children = _children.get();
            }
            return new ArrayList<Resource>(children.values());
        }

        public boolean exists()
        {
            return _files != null && !_files.isEmpty();
        }

        public boolean isCollection()
        {
            return exists() && _files.get(0).isDirectory();
        }

        public Resource find(String name)
        {
            if (null == _children.get())
                list();
            Resource r = _children.get().get(name);
            if (r == null && AppProps.getInstance().isDevMode())
            {
                for (File dir : _files)
                {
                    // might not be case sensitive, but this is just devmode
                    File f = new File(dir,name);
                    if (f.exists())
                        return new StaticResource(WebdavResolverImpl.c(getPath(),f.getName()), new ArrayList<File>(Collections.singletonList(f)));
                }
            }
            return r;
        }

        public boolean isFile()
        {
            return exists() && _files.get(0).isFile();
        }

        public List<String> listNames()
        {
            if (null == _children.get())
                list();
            return new ArrayList<String>(_children.get().keySet());
        }

        public long getCreation()
        {
            return 0;
        }

        public long getLastModified()
        {
            return exists() ? _files.get(0).lastModified() : 0;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            assert isFile();
            if (isFile())
                return new FileInputStream(_files.get(0));
            return null;
        }

        public long copyFrom(User user, InputStream in) throws IOException
        {
            throw new UnsupportedOperationException(); 
        }

        public long getContentLength()
        {
            assert isFile();
            if (isFile())
                return _files.get(0).length();
            return 0;
        }

        @Override
        public String getETag()
        {
            // In dev mode don't cache the etag!
            if (null == _etag || AppProps.getInstance().isDevMode())
                _etag = "W/\"" + getLastModified() + "\"";
            return _etag;
        }
    }

    class ServletResource extends _PublicResource
    {
        URL _url;
        boolean _file;

        ServletResource(String path)
        {
            super(path);
            _url = getResource(path);
        }

        public boolean exists()
        {
            return _url != null;
        }

        public boolean isCollection()
        {
            return false;
        }

        public Resource find(String name)
        {
            return null;
        }

        public boolean isFile()
        {
            return true;
        }

        public List<String> listNames()
        {
            return Collections.EMPTY_LIST;
        }

        public List<Resource> list()
        {
            return Collections.EMPTY_LIST;
        }

        public long getCreation()
        {
            return 0;
        }

        public long getLastModified()
        {
            return 0;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            if (exists())
                return _url.openStream();
            return null;
        }

        public long copyFrom(User user, InputStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public long getContentLength() throws IOException
        {
            return _url.openConnection().getContentLength();
        }
    }

    // should get the WebdavServlet context somehow
    URL getResource(String path)
    {
        ServletContext c = ViewServlet.getViewServletContext();
        if (c != null)
        {
            try
            {
                return c.getResource(path);
            }
            catch (MalformedURLException x)
            {
                /* */
            }
        }
        return null;
    }
}
