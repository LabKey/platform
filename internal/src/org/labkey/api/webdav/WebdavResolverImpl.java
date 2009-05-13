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
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.*;
import org.labkey.api.collections.TTLCacheMap;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:07:13 PM
 */
public class WebdavResolverImpl implements WebdavResolver
{
    static WebdavResolverImpl _instance = new WebdavResolverImpl();

    private WebdavResolverImpl()
    {
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    public boolean requiresLogin()
    {
        return false;
    }

    public Resource lookup(String fullPath)
    {
        if (fullPath == null)
            return null;

        Resource root = getRoot();
        if (fullPath.equals("/"))
            return root;

        // start at the root and work down, to avoid lots of cache misses
        ArrayList<String> paths = FileUtil.normalizeSplit(fullPath);
        if (paths == null)
            return null;

        Resource resource = root;
        int depth = 0;
        for (String name : paths)
        {
            Resource r = resource.find(name);
            // short circuit the descent at last web folder
            if (null == r  || r instanceof UnboundResource)
                return new UnboundResource(c(resource.getPath(),paths.subList(depth,paths.size())));
            depth++;
            resource = r;
        }
        if (null == resource)
            resource = new UnboundResource(fullPath);
        return resource;
    }


    WebFolderResource _root = null;

    synchronized Resource getRoot()
    {
        if (null == _root)
            _root = new WebFolderResource(ContainerManager.getRoot(), null);
        return _root;
    }


    /** short lived cache to make webdav perform reasonably */
    class FolderCache extends TTLCacheMap<String,Resource> implements ContainerManager.ContainerListener
    {
        FolderCache()
        {
            super(1000, 5 * TTLCacheMap.MINUTE);
            ContainerManager.addContainerListener(this);
        }

        public synchronized Resource put(String key, Resource value)
        {
            return super.put(key,value);
        }

        public synchronized Resource get(String key)
        {
            return super.get(key);
        }

        public synchronized Resource remove(String key)
        {
            return super.remove(key);
        }

        public void containerCreated(Container c)
        {
            invalidate(getParentPath(c), false);
        }

        public void containerDeleted(Container c, User user)
        {
            invalidate(c.getPath(), true);
            invalidate(getParentPath(c), false);
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
                    case ACL:
                    case AttachmentDirectory:
                    case WebRoot:
                    default:
                    {
                        invalidate(c.getPath(), true);
                        break;
                    }
                    case Name:
                    {
                        String oldName = (String)evt.getOldValue();
                        invalidate(c.getPath(), true);
                        invalidate(c(getParentPath(c),oldName), true);
                        invalidate(getParentPath(c), false);
                        break;
                    }
                    case Parent:
                    {
                        Container oldParent = (Container)pce.getOldValue();
                        invalidate(c.getPath(), true);
                        invalidate(getParentPath(c), false);
                        invalidate(c(oldParent.getPath(),c.getName()), true);
                        invalidate(oldParent.getPath(), false);
                        break;
                    }
                }
            }
            catch (Exception x)
            {
                clear();
            }
        }

        String getParentPath(Container c)
        {
            return c.getParent() == null ? "" : c.getParent().getPath();
        }

        void invalidate(String path, boolean recursive)
        {
            remove(path);
            if (recursive)
                removeUsingPrefix(c(path,""));
            if (path.equals("/"))
            {
                synchronized (WebdavResolverImpl.this)
                {
                    _root = null;
                }
            }
        }
    }

    FolderCache _folderCache = new FolderCache();


//    private FolderResourceImpl lookupWebFolder(String folder)
//    {
//        boolean isPipelineLink = false;
//        assert(folder.equals("/") || !folder.endsWith("/"));
//        if (!folder.equals("/") && folder.endsWith("/"))
//            folder = folder.substring(0,folder.length()-1);

//        if (folder.endsWith("/" + WIKI_LINK))
//        {
//            folder = folder.substring(0, folder.length()- WIKI_LINK.length()-1);
//            Container c = ContainerManager.getForPath(folder);
//            return new WikiFolderResource(c);
//        }
        
//        if (folder.endsWith("/" + PIPELINE_LINK))
//        {
//            isPipelineLink = true;
//            folder = folder.substring(0, folder.length()- PIPELINE_LINK.length()-1);
//        }

//        Container c = ContainerManager.getForPath(folder);
//        if (null == c)
//        {
//
//            return null;
//        }
//
//        // normalize case of folder
//        folder = isPipelineLink ? c(c,PIPELINE_LINK) : c.getPath();
//
//        FolderResourceImpl resource = _folderCache.get(folder);
//        if (null != resource)
//            return resource;
//
//        if (isPipelineLink)
//        {
//            PipeRoot root = null;
//            try
//            {
//                root = PipelineService.get().findPipelineRoot(c);
//                if (null == root)
//                    return null;
//            }
//            catch (SQLException x)
//            {
//                Logger.getLogger(WebdavResolverImpl.class).error("unexpected exception", x);
//            }
//            resource = new PipelineFolderResource(c, root);
//        }
//        else
//        {
//            AttachmentDirectory dir = null;
//            try
//            {
//                try
//                {
//                    if (c.isRoot())
//                        dir = AttachmentService.get().getMappedAttachmentDirectory(c, false);
//                    else
//                        dir = AttachmentService.get().getMappedAttachmentDirectory(c, true);
//                }
//                catch (AttachmentService.MissingRootDirectoryException  ex)
//                {
//                    /* */
//                }
//            }
//            catch (AttachmentService.UnsetRootDirectoryException x)
//            {
//                /* */
//            }
//            resource = new WebFolderResource(c, dir);
//        }
//
//        _folderCache.put(folder,resource);
//        return resource;
//    }

    public class WebFolderResource extends AbstractCollectionResource implements WebFolder
    {
        final Container _c;
        final AttachmentDirectory _attachmentDirectory;
        final Resource _attachmentResource;
        ArrayList<String> _children = null;

        WebFolderResource(Container c, AttachmentDirectory root)
        {
            super(c.getPath());
            _c = c;
            _policy = c.getPolicy();
            _attachmentDirectory = root;
            if (null != _attachmentDirectory)
                _attachmentResource = AttachmentService.get().getAttachmentResource(getPath(), _attachmentDirectory);
            else
                _attachmentResource = null;
        }

        public int getIntPermissions(User user)
        {
            return _policy.getPermsAsOldBitMask(user);
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

        public synchronized List<String> getWebFoldersNames(User user)
        {
            if (null ==_children)
            {
                List<Container> list = ContainerManager.getChildren(_c);
                _children = new ArrayList<String>(list.size() + 2);
                for (Container aList : list)
                    _children.add(aList.getName());

                for (WebdavService.Provider p : WebdavService.getProviders())
                {
                    Set<String> s = p.addChildren(this);
                    if (s != null)
                        _children.addAll(s);
                }
            }

            if (null == user || _children.size() == 0)
                return _children;

            ArrayList<String> ret = new ArrayList<String>();
            for (String name : _children)
            {
                Resource r = lookup(WebdavResolverImpl.c(this,name));
                if (null != r && r.canRead(user))
                    ret.add(name);
            }
            return ret;
        }


        @Override 
        public boolean canCreateCollection(User user)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user)
        {
            return null != _attachmentResource && _attachmentResource.canCreate(user);
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
        public boolean canWrite(User user)
        {
            return false;
        }


        @NotNull
        public List<String> listNames()
        {
            Set<String> set = new TreeSet<String>();
            if (null != _attachmentResource)
            set.addAll(_attachmentResource.listNames());
            set.addAll(getWebFoldersNames(null));
            ArrayList<String> list = new ArrayList<String>(set);
            Collections.sort(list);
            return list;
        }


        public Resource find(String child)
        {
            String name = null;
            for (String folder : getWebFoldersNames(null))
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
                String path = c(getPath(), name);
                // check in webfolder cache
                Resource resource = _folderCache.get(path);
                if (null != resource)
                    return resource;

                if (c != null)
                {
                    AttachmentDirectory dir = null;
                    try
                    {
                        try
                        {
                            if (c.isRoot())
                                dir = AttachmentService.get().getMappedAttachmentDirectory(c, false);
                            else
                                dir = AttachmentService.get().getMappedAttachmentDirectory(c, true);
                        }
                        catch (AttachmentService.MissingRootDirectoryException  ex)
                        {
                            /* */
                        }
                    }
                    catch (AttachmentService.UnsetRootDirectoryException x)
                    {
                        /* */
                    }
                    resource = new WebFolderResource(c, dir);
                }
                else
                {
                    for (WebdavService.Provider p : WebdavService.getProviders())
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

            if (null != _attachmentResource)
            {
                Resource r = _attachmentResource.find(child);
                if (null != r)
                    return r;
            }
            return new UnboundResource(WebdavResolverImpl.c(this,child));
        }


        @NotNull
        public List<History> getHistory()
        {
            return Collections.EMPTY_LIST;
        }
    }


    public static class UnboundResource extends AbstractResource
    {
        UnboundResource(String path)
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



        public Resource find(String name)
        {
            return new UnboundResource(WebdavResolverImpl.c(this,name));
        }

        public List<String> listNames()
        {
            return Collections.emptyList();
        }

        public List<Resource> list()
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
        public List<History> getHistory()
        {
            return Collections.EMPTY_LIST;
        }
    }

    String c(Container container, String... names)
    {
        return c(container.getPath(), names);
    }

    static String c(Resource r, String... names)
    {
        return c(r.getPath(), names);
    }

    static String c(String path, List<String> names)
    {
        return c(path, names.toArray(new String[names.size()]));
    }

    static String c(String path, String... names)
    {
        StringBuilder s = new StringBuilder();
        s.append(StringUtils.stripEnd(path,"/"));
        for (String name : names)
            s.append("/").append(StringUtils.strip(name, "/"));
        return s.toString();
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testContainers() throws SQLException
        {
            TestContext context = TestContext.get();
            User guest = UserManager.getGuestUser();
            User user = context.getUser();
            assertTrue("login before running this test", null != user);
            assertFalse("login before running this test", user.isGuest());
            Container c = JunitUtil.getTestContainer();

            WebdavResolver resolver = WebdavResolverImpl.get();

            assertNull(resolver.lookup(".."));
            assertNull(resolver.lookup("/.."));
            assertNull(resolver.lookup(c.getPath() + "/./../../.."));

            Resource root = resolver.lookup("/");
            assertNotNull(root);
            assertTrue(root.isCollection());
            assertTrue(root.canRead(user));
            assertFalse(root.canCreate(user));

            Resource junit = resolver.lookup(c.getPath());
            assertNotNull(junit);
            assertTrue(junit.isCollection());

            String pathTest = junit.getPath() + "/dav";
            Container cTest = ContainerManager.ensureContainer(pathTest);

            SecurityPolicy policyNone = new SecurityPolicy(cTest);
            policyNone.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), NoPermissionsRole.class);
            policyNone.addRoleAssignment(user, ReaderRole.class);
            SecurityManager.savePolicy(policyNone);

            Resource rTest = resolver.lookup(pathTest);
            assertNotNull(rTest);
            assertTrue(rTest.canRead(user));
            assertFalse(rTest.canWrite(user));
            assertNotNull(rTest.parent());
            assertTrue(rTest.parent().isCollection());

            List<String> names = resolver.lookup(junit.getPath()).listNames();
            assertFalse(names.contains("webdav"));
            assertTrue(names.contains("dav"));

            SecurityPolicy policyRead = new SecurityPolicy(cTest);
            policyRead.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), ReaderRole.class);
            SecurityManager.savePolicy(policyRead);
            rTest = resolver.lookup(pathTest);
            assertTrue(rTest.canRead(guest));

            ContainerManager.rename(cTest, "webdav");
            String pathNew = junit.getPath() + WebdavService.getServletPath();
//            Container cTestNew = ContainerManager.getForPath(pathNew);
            assertFalse(resolver.lookup(pathTest).exists());
            assertNotNull(resolver.lookup(pathNew));

            names = resolver.lookup(junit.getPath()).listNames();
            assertTrue(names.contains("webdav"));
            assertFalse(names.contains("dav"));

            Resource rNotFound = resolver.lookup("/NotFound/" + GUID.makeHash());
            assertFalse(rNotFound.exists());
        }


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


        public void testFileContent()
        {

        }


        public void testPipeline()
        {

        }


        @Override
        protected void tearDown() throws Exception
        {
            Container c = JunitUtil.getTestContainer();
            deleteContainer(c.getPath() + "/dav");
            deleteContainer(c.getPath() + "/webdav");
        }

        void deleteContainer(String path)
        {
             Container x = ContainerManager.getForPath(path);
            if (null != x)
                ContainerManager.delete(x, TestContext.get().getUser());
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
