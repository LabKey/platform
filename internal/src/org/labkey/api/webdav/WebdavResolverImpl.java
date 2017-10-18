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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Filter;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Random;


/**
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:07:13 PM
 */
public class WebdavResolverImpl extends AbstractWebdavResolver
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

    public Path getRootPath()
    {
        return _rootPath;
    }

    WebFolderResource _root = null;

    protected synchronized WebdavResource getRoot()
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

    private class WebdavListener extends AbstractWebdavListener
    {
        @Override
        protected void clearFolderCache()
        {
            _folderCache.clear();
        }

        @Override
        protected void invalidate(Path containerPath, boolean recursive)
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

    // Cache with short-lived entries to make webdav perform reasonably.  WebdavResolverImpl is a singleton, so we
    // end up with just one of these.
    private Cache<Path, WebdavResource> _folderCache = CacheManager.getCache(CacheManager.UNLIMITED, 5 * CacheManager.MINUTE, "WebDAV folders");

    public class WebFolderResource extends AbstractWebFolderResource
    {
        WebFolderResource(WebdavResolver resolver, Container c)
        {
            super(resolver, c);
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
