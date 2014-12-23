/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Filter;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;


/**
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:07:13 PM
 */
public class WebdavResolverImpl implements WebdavResolver
{
    static WebdavResolverImpl _instance = new WebdavResolverImpl(WebdavService.getPath());

    final Path _rootPath;

    private WebdavResolverImpl(Path path)
    {
        _rootPath = path;
        ContainerManager.addContainerListener(new WebdavListener());
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    public boolean requiresLogin()
    {
        return false;
    }

    public WebdavResource welcome()
    {
        return lookup(Path.rootPath);
    }



    public Path getRootPath()
    {
        return _rootPath;
    }

    public WebdavResource lookup(Path fullPath)
    {
        if (fullPath == null || !fullPath.startsWith(getRootPath()))
            return null;
        Path path = getRootPath().relativize(fullPath).normalize();

        WebdavResource root = getRoot();
        if (path.size() == 0)
            return root;

        // start at the root and work down, to avoid lots of cache misses
        WebdavResource resource = root;
        for (String name : path)
        {
            WebdavResource r = resource.find(name);
            // short circuit the descent at last web folder
            if (null == r  || r instanceof UnboundResource)
                return new UnboundResource(fullPath);
            resource = r;
        }
        if (null == resource)
            resource = new UnboundResource(fullPath);
        return resource;
    }


    WebFolderResource _root = null;

    synchronized WebdavResource getRoot()
    {
        if (null == _root)
        {
            _root = new WebFolderResource(this, ContainerManager.getRoot())
            {
                @Override
                public boolean canList(User user, boolean forRead)
                {
                    return true;
                }
            };
        }
        return _root;
    }

    @Override
    public String toString()
    {
        return "webdav";
    }


    private class WebdavListener extends ContainerManager.AbstractContainerListener
    {
        public void containerCreated(Container c, User user)
        {
            invalidate(c.getParsedPath().getParent(), false);
        }

        public void containerDeleted(Container c, User user)
        {
            invalidate(c.getParsedPath(), true);
            invalidate(c.getParsedPath().getParent(), false);
        }

        public void propertyChange(PropertyChangeEvent pce)
        {
            ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)pce;
            Container c = evt.container;
            try
            {
                switch (evt.property)
                {
                    case PipelineRoot:
                    case Policy:
                    case AttachmentDirectory:
                    case WebRoot:
                    default:
                    {
                        invalidate(c.getParsedPath(), true);
                        break;
                    }
                    case Name:
                    {
                        String oldName = (String)evt.getOldValue();
                        invalidate(c.getParsedPath(), true);
                        invalidate(resolveSibling(c, oldName), true);
                        invalidate(c.getParsedPath().getParent(), false);
                        break;
                    }
                    case Parent:
                    {
                        Container oldParent = (Container)pce.getOldValue();
                        invalidate(c.getParsedPath(), true);
                        invalidate(getParentPath(c), false);
                        invalidate(resolveSibling(c,c.getName()), true);
                        invalidate(oldParent.getParsedPath(), false);
                        break;
                    }
                    case SiteRoot:
                        _folderCache.clear();
                        break;
                }
            }
            catch (Exception x)
            {
                _folderCache.clear();
            }
        }


        Path getParentPath(Container c)
        {
            Path p = c.getParsedPath();
            if (p.size() == 0)
                throw new IllegalArgumentException();
            return p.getParent();
        }


        Path resolveSibling(Container c, String name)
        {
            Path p = c.getParsedPath();
            if (p.size() == 0)
                throw new IllegalArgumentException();
            return p.getParent().append(name);
        }


        void invalidate(Path containerPath, boolean recursive)
        {
            final Path path = getRootPath().append(containerPath);
            _folderCache.remove(path);
            if (recursive)
                _folderCache.removeUsingFilter(new Filter<Path>() {
                    @Override
                    public boolean accept(Path test)
                    {
                        return test.startsWith(path);
                    }
                });
            if (containerPath.size() == 0)
            {
                synchronized (WebdavResolverImpl.this)
                {
                    _root = null;
                }
            }
        }
    }


    // Cache with short-lived entries to make webdav perform reasonably.  WebdavResolvedImpl is a singleton, so we
    // end up with just one of these.
    private Cache<Path, WebdavResource> _folderCache = CacheManager.getCache(CacheManager.UNLIMITED, 5 * CacheManager.MINUTE, "WebDAV folders");

    public class WebFolderResource extends AbstractWebdavResourceCollection implements WebdavResolver.WebFolder
    {
        WebdavResolver _resolver;
        final Container _c;
        ArrayList<String> _children = null;

        WebFolderResource(WebdavResolver resolver, Container c)
        {
            super(resolver.getRootPath().append(c.getParsedPath()), resolver);
            _resolver = resolver;
            _c = c;
            _containerId = c.getId();
            setPolicy(c.getPolicy());
        }

        @Override
        public long getCreated()
        {
            return null != _c && null != _c.getCreated() ? _c.getCreated().getTime() : Long.MIN_VALUE;
        }

        @Override
        public User getCreatedBy()
        {
            return UserManager.getUser(_c.getCreatedBy());
        }

        @Override
        public long getLastModified()
        {
            return getCreated();
        }

        @Override
        public User getModifiedBy()
        {
            return getCreatedBy();
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            // context
            Path contextPath = null==context ? AppProps.getInstance().getParsedContextPath() : Path.parse(context.getContextPath());
            // _webdav
            Path path = contextPath.append(getPath().get(0)).append(getContainerId());
            return path.encode("/", "/");
        }

        public Container getContainer()
        {
            return _c;
        }

        public boolean exists()
        {
            return true;
        }

        public boolean isCollection()
        {
            return exists();
        }

        public synchronized List<String> getWebFoldersNames()
        {
            if (null == _children)
            {
                List<Container> list = ContainerManager.getChildren(_c);
                ArrayList<String> children = new ArrayList<>(list.size() + 2);
                for (Container aList : list)
                    children.add(aList.getName());

                for (WebdavService.Provider p : WebdavService.get().getProviders())
                {
                    Set<String> s = p.addChildren(this);
                    if (s != null)
                        children.addAll(s);
                }
                // providers might not be registred if !isStartupComplete();
                if (!ModuleLoader.getInstance().isStartupComplete())
                    return children;
                _children = children;
            }
            return _children;
        }


        @Override 
        public boolean canCreateCollection(User user, boolean forCreate)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return false;
//            return null != _attachmentResource && _attachmentResource.canCreate(user);
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
        public boolean canWrite(User user, boolean forWrite)
        {
            return false;
        }


        @NotNull
        public Collection<String> listNames()
        {
            Set<String> set = new TreeSet<>();
//            if (null != _attachmentResource)
//                set.addAll(_attachmentResource.listNames());
            set.addAll(getWebFoldersNames());
            ArrayList<String> list = new ArrayList<>(set);
            Collections.sort(list);
            return list;
        }


        public WebdavResource find(String child)
        {
            String name = null;
            for (String folder : getWebFoldersNames())
            {
                if (folder.equalsIgnoreCase(child))
                {
                    name = folder;
                    break;
                }
            }
            Container c = getContainer().getChild(child);
            if (name == null && c != null)
                name = c.getName();

            if (name != null)
            {
                Path path = getPath().append(name);
                // check in webfolder cache
                WebdavResource resource = _folderCache.get(path);
                if (null != resource)
                    return resource;

                if (c != null)
                {
                    resource = new WebFolderResource(_resolver, c);
                }
                else
                {
                    for (WebdavService.Provider p : WebdavService.get().getProviders())
                    {
                        resource = p.resolve(this, name);
                        if (null != resource)
                            break;
                    }
                }

                if (resource != null)
                {
                    _folderCache.put(path, resource);
                    return resource;
                }
            }

            return new UnboundResource(this.getPath().append(child));
        }
    }


    public static class UnboundResource extends AbstractWebdavResource
    {
        UnboundResource(String path)
        {
            super(Path.parse(path));
        }

        UnboundResource(Path path)
        {
            super(path);
        }

        public boolean exists()
        {
            return false;
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isFile()
        {
            return false;
        }

        @Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            return Collections.emptySet();
        }



        public WebdavResource find(String name)
        {
            return new UnboundResource(this.getPath().append(name));
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
            return null;
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public long getContentLength()
        {
            return 0;
        }

        @NotNull
        public Collection<History> getHistory()
        {
            return Collections.emptyList();
        }
    }


    public static class TestCase extends Assert
    {
        private Container testContainer = null;
        
        @Test
        public void testContainers() throws SQLException
        {
            TestContext context = TestContext.get();
            User guest = UserManager.getGuestUser();
            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());
            
            Container junitContainer = JunitUtil.getTestContainer();
            testContainer = ContainerManager.createContainer(junitContainer, "c" + (new Random().nextInt()));
            Container c = testContainer;

            WebdavResolver resolver = WebdavResolverImpl.get();

            assertNull(resolver.lookup(Path.parse("..")));
            assertNull(resolver.lookup(Path.parse("/..")));
            assertNull(resolver.lookup(Path.parse(c.getPath() + "/./../../..")));

            Path rootPath = resolver.getRootPath();
            WebdavResource root = resolver.lookup(rootPath);
            assertNotNull(root);
            assertTrue(root.isCollection());
            assertTrue(root.canRead(user,true));
            assertFalse(root.canCreate(user,true));

            WebdavResource junit = resolver.lookup(rootPath.append(c.getParsedPath()));
            assertNotNull(junit);
            assertTrue(junit.isCollection());

            Path pathTest = c.getParsedPath().append("dav");
            Container cTest = ContainerManager.ensureContainer(pathTest.toString());

            MutableSecurityPolicy policyNone = new MutableSecurityPolicy(cTest);
            policyNone.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), NoPermissionsRole.class);
            policyNone.addRoleAssignment(user, ReaderRole.class);
            SecurityPolicyManager.savePolicy(policyNone);

            WebdavResource rTest = resolver.lookup(rootPath.append(pathTest));
            assertNotNull(rTest);
            assertTrue(rTest.canRead(user,true));
            assertFalse(rTest.canWrite(user,true));
            assertNotNull(rTest.parent());
            assertTrue(rTest.parent().isCollection());


            Collection<String> names = resolver.lookup(junit.getPath()).listNames();
            assertNotNull(resolver.lookup(junit.getPath().append("webdav")));
            assertNotNull(names.contains("dav"));

            MutableSecurityPolicy policyRead = new MutableSecurityPolicy(cTest);
            policyRead.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), ReaderRole.class);
            SecurityPolicyManager.savePolicy(policyRead);
            rTest = resolver.lookup(rootPath.append(pathTest));
            assertTrue(rTest.canRead(guest,true));

            ContainerManager.rename(cTest, user, "webdav");
            Path pathNew = c.getParsedPath().append("webdav");
            assertFalse(resolver.lookup(rootPath.append(pathTest)).exists());
            assertTrue(resolver.lookup(rootPath.append(pathNew)).exists());

            names = resolver.lookup(junit.getPath()).listNames();
            assertTrue(names.contains("webdav"));
            assertFalse(names.contains("dav"));

            WebdavResource rNotFound = resolver.lookup(rootPath.append("NotFound").append(GUID.makeHash()));
            assertFalse(rNotFound.exists());
        }


        @Test
        public void testNormalize()
        {
            assertNull(FileUtil.normalize(".."));
            assertNull(FileUtil.normalize("/.."));
            assertNull(FileUtil.normalize("/./.."));
            assertEquals(FileUtil.normalize("/dir//down"), "/dir/down");
            assertNull(FileUtil.normalize("/dir/../down/../.."));
            assertEquals(FileUtil.normalize("./dir/..//"), "/");
            assertEquals(FileUtil.normalize("/dir/./../down/"), "/down");
        }


        @Test
        public void testFileContent()
        {

        }


        @Test
        public void testPipeline()
        {

        }


        @After
        public void tearDown() throws Exception
        {
            if (null != testContainer)
            {
                deleteContainer(testContainer.getParsedPath().append("dav"));
                deleteContainer(testContainer.getParsedPath().append("webdav"));
                deleteContainer(testContainer.getParsedPath());
                testContainer = null;
            }
        }
        

        void deleteContainer(Path path)
        {
            Container x = ContainerManager.getForPath(path);
            if (null != x)
                ContainerManager.delete(x, TestContext.get().getUser());
        }
    }
}
