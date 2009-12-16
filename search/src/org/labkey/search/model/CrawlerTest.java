package org.labkey.search.model;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.api.util.Path;
import org.labkey.api.util.Pair;
import org.labkey.api.util.GUID;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.security.*;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.data.Container;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 12, 2009
 * Time: 3:51:51 PM
 */
public class CrawlerTest extends junit.framework.TestCase
{
    public CrawlerTest()
    {
        super();
    }


    public CrawlerTest(String name)
    {
        super(name);
    }


    public static Test suite()
    {
        return new TestSuite(CrawlerTest.class);
    }

    
    public void test() throws Exception
    {
        DavCrawler cr = new DavCrawler();
        cr.setResolver(new TestResolver(ModuleLoader.getInstance().getCoreModule().getExplodedPath()));
        cr.startFull(Path.rootPath);

        long start = System.currentTimeMillis();
    }

    
    //
    // TEST
    //

    class TestResolver implements WebdavResolver, SecurableResource
    {
        final File _base;
        final SecurityPolicy _policy;

        TestResolver(File f)
        {
            _base = f;
            MutableSecurityPolicy policy = new MutableSecurityPolicy(this);
            policy.addRoleAssignment(UserManager.getGuestUser(), ReaderRole.class);
            policy.addRoleAssignment(User.getSearchUser(), ReaderRole.class);
            _policy = policy;
        }
        
        public boolean requiresLogin()
        {
            return false;
        }

        public Path getRootPath()
        {
            return Path.rootPath;
        }

        public Resource lookup(Path path)
        {
            return new FileSystemResource(path, new File(_base, path.toString()), _policy);
        }

        public Resource welcome()
        {
            return null;
        }

        // SecurableResource
        String _guid = GUID.makeGUID();

        @NotNull
        public String getResourceId()
        {
            return _guid;
        }

        @NotNull
        public String getResourceName()
        {
            return _base.getName();
        }

        @NotNull
        public String getResourceDescription()
        {
            return null;
        }

        @NotNull
        public Set<Class<? extends Permission>> getRelevantPermissions()
        {
            return null;
        }

        @NotNull
        public Module getSourceModule()
        {
            return null;
        }

        public SecurableResource getParentResource()
        {
            return null;
        }

        @NotNull
        public Container getResourceContainer()
        {
            return null;
        }

        @NotNull
        public List<SecurableResource> getChildResources(User user)
        {
            return null;
        }

        public boolean mayInheritPolicy()
        {
            return false;
        }
    }


    class TestSavePaths implements DavCrawler.SavePaths
    {
        Map<Path, Pair<Date,Date>> collections = new HashMap<Path,Pair<Date,Date>>();
        Map<Path, Date> files = new HashMap<Path, Date>();

        public synchronized boolean updatePath(Path path, Date lastIndexed, Date nextCrawl, boolean create)
        {
            collections.put(path, new Pair<Date,Date>(lastIndexed,nextCrawl));
            return true;
        }

        public synchronized void updatePrefix(Path path, Date lastIndexed, Date nextCrawl, boolean force)
        {
            for (Map.Entry<Path,Pair<Date,Date>> e : collections.entrySet())
            {
                if (e.getKey().startsWith(path))
                    updatePath(e.getKey(), lastIndexed, nextCrawl, false);
            }
        }

        public synchronized void deletePath(Path path)
        {
            collections.remove(path);
        }

        public synchronized Map<Path, Date> getPaths(int limit)
        {
            long now = System.currentTimeMillis();
            limit = Math.min(limit,5);
            Map<Path, Date> ret = new TreeMap<Path,Date>();
            for (Map.Entry<Path,Pair<Date,Date>> e : collections.entrySet())
            {
                Date nextCrawl = e.getValue().second;
                if (nextCrawl.getTime() < now)
                    ret.put(e.getKey(), nextCrawl);
                if (ret.size() == limit)
                    break;
            }
            return ret;
        }

        public synchronized Map<String, Date> getFiles(Path path)
        {
            Map<String,Date> ret = new TreeMap<String,Date>();
            for (Map.Entry<Path,Date> e : files.entrySet())
            {
                if (e.getKey().startsWith(path))
                    ret.put(e.getKey().getName(), e.getValue());
            }
            return ret;
        }

        public synchronized boolean updateFile(Path path, Date lastIndexed)
        {
            files.put(path, lastIndexed);
            return true;
        }
    }
}
