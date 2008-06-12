/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.core.webdav;

import org.labkey.api.util.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.wiki.WikiService;
import org.labkey.core.ftp.FtpController;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;
import java.net.URI;
import java.sql.SQLException;
import java.beans.PropertyChangeEvent;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:07:13 PM
 */
public class WebdavResolverImpl implements WebdavResolver
{
    static String PIPELINE_LINK = "@pipeline";
    static String WIKI_LINK = "@wiki";
    static boolean wikiDemo = false;

    static WebdavResolverImpl _instance = new WebdavResolverImpl();

    private WebdavResolverImpl()
    {
    }

    public static WebdavResolver get()
    {
        return _instance;
    }


    public Resource lookup(String fullPath)
    {
        if (fullPath == null)
            return null;

        FolderResourceImpl root = lookupWebFolder("/");
        if (fullPath.equals("/"))
            return root;

        // start at the root and work down, to avoid lots of cache misses
        ArrayList<String> paths = FileUtil.normalizeSplit(fullPath);
        if (paths == null)
            return null;

        FolderResourceImpl folder = root;
        int depth = 0;
        for (String name : paths)
        {
            ResourceImpl f = folder.find(name);
            // short circuit the descent at last web folder
            if (null == f || !f.isWebFolder())
                break;
            depth++;
            folder = (FolderResourceImpl)f;
        }
        if (folder == null)
            return null;
        if (depth == paths.size())
            return folder;

        String relPath = StringUtils.join(paths.subList(depth,paths.size()), "/");

        // this is messy

        Resource resource = null;
        if (folder instanceof WikiFolderResource)
        {
            if (depth == paths.size()-1)
                resource = folder.find(relPath);
        }
        else if (folder.isWebFolder())
        {
            // don't return FileResource if there is no file system
            if (!folder.isVirtual())
                resource = new FileResource(folder, relPath);
        }
        if (null == resource)
            resource = new UnboundResource(c(folder,relPath));
        return resource;
    }


    /** short lived cache to make webdav perform reasonably */
    class FolderCache extends TTLCacheMap<String,FolderResourceImpl> implements ContainerManager.ContainerListener
    {
        FolderCache()
        {
            super(1000, 5 * TTLCacheMap.MINUTE);
            ContainerManager.addContainerListener(this);
        }

        public synchronized FolderResourceImpl put(String key, FolderResourceImpl value)
        {
            return super.put(key,value);
        }

        public synchronized FolderResourceImpl get(String key)
        {
            return super.get(key);
        }

        public synchronized FolderResourceImpl remove(String key)
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
                    case ACL:
                    case AttachmentDirectory:
                    case PipelineRoot:
                    case WebRoot:
                    default:
                    {
                        invalidate(c.getPath(), false);
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
        }
    }

    FolderCache _folderCache = new FolderCache();


    private FolderResourceImpl lookupWebFolder(String folder)
    {
        boolean isPipelineLink = false;
        assert(folder.equals("/") || !folder.endsWith("/"));
        if (!folder.equals("/") && folder.endsWith("/"))
            folder = folder.substring(0,folder.length()-1);

        if (wikiDemo && folder.endsWith("/" + WIKI_LINK))
        {
            folder = folder.substring(0, folder.length()- WIKI_LINK.length()-1);
            Container c = ContainerManager.getForPath(folder);
            return new WikiFolderResource(c);
        }
        
        if (folder.endsWith("/" + PIPELINE_LINK))
        {
            isPipelineLink = true;
            folder = folder.substring(0, folder.length()- PIPELINE_LINK.length()-1);
        }

        Container c = ContainerManager.getForPath(folder);
        if (null == c)
            return null;

        // normalize case of folder
        folder = isPipelineLink ? c(c,PIPELINE_LINK) : c.getPath();

        FolderResourceImpl resource = _folderCache.get(folder);
        if (null != resource)
            return resource;

        if (isPipelineLink)
        {
            PipeRoot root = null;
            try
            {
                root = PipelineService.get().findPipelineRoot(c);
                if (null == root)
                    return null;
            }
            catch (SQLException x)
            {
                Logger.getLogger(FtpController.class).error("unexpected exception", x);
            }
            resource = new PipelineFolderResource(c, root);
        }
        else
        {
            AttachmentDirectory dir = null;
            try
            {
                if (c.isRoot())
                    dir = AttachmentService.get().getMappedAttachmentDirectory(c, false);
                else
                    dir = AttachmentService.get().getMappedAttachmentDirectory(c, true);
            }
            catch (AttachmentService.UnsetRootDirectoryException x)
            {
                /* */
            }
            resource = new WebFolderResource(c, dir);
        }

        _folderCache.put(folder,resource);
        return resource;
    }


    public abstract class ResourceImpl implements Resource
    {
        long _ts = System.currentTimeMillis();
        private String _path;
        protected Container _c;                                 // if this corresponds exactly to a web folder
        protected ACL _acl;
        protected File _root;
        protected File _file;
        private String _etag = null;

        ResourceImpl(String path)
        {
            this._path = path;
            assert _path.equals("/") || !_path.endsWith("/");
        }

        ResourceImpl(String folder, String name)
        {
            this(c(folder,name));
        }

        ResourceImpl(ResourceImpl folder, String name)
        {
            this(c(folder,name));
        }
        
        public String getPath()
        {
            return _path;
        }


        public String getName()
        {
            String p = _path;
            if (p.endsWith("/"))
                p = _path.substring(0,p.length()-1);
            int i = p.lastIndexOf("/");
            return p.substring(i+1);
        }


        public boolean exists()
        {
            return _file == null || _file.exists();
        }


        public boolean isCollection()
        {
            if (null != _file && _file.isDirectory())
                return true;
            return _path.endsWith("/");
        }


        // cannot create objects in a virtual collection
        public boolean isVirtual()
        {
            return _file == null;
        }


        public boolean isFile()
        {
            return _file != null && _file.isFile();
        }


        public File getFile()
        {
            return _file;
        }


        public InputStream getInputStream() throws IOException
        {
            if (null == _file || !_file.exists())
                return null;
            return new FileInputStream(_file);
        }


        public OutputStream getOutputStream() throws IOException
        {
            if (null == _file || !_file.exists())
                return null;
            return new FileOutputStream(_file);
        }
        

        public List<String> listNames()
        {
            if (!isCollection())
                return Collections.emptyList();
            ArrayList<String> list = new ArrayList<String>();
            if (_file != null && _file.isDirectory())
            {
                File[] files = _file.listFiles();
                if (null != files)
                {
                    for (File file: files)
                        list.add(file.getName());
                }
            }
            return list;
        }


        public List<Resource> list()
        {
            List<String> names = listNames();
            ArrayList<Resource> infos = new ArrayList<Resource>(names.size());
            for (String name : names)
            {
                Resource r = find(name);
                if (null != r && !(r instanceof UnboundResource))
                    infos.add(r);
            }
            return infos;
        }


        public Resource parent()
        {
            if ("/".equals(_path))
                return null;
            String parent = _path.endsWith("/") ? _path.substring(0, _path.length()-1) : _path;
            parent = parent.substring(0,parent.lastIndexOf("/")+1);
            return lookup(parent);
        }


        public long getCreation()
        {
            if (_c != null && _c.getCreated() != null)
                return _c.getCreated().getTime();
            if (null != _file)
                return _file.lastModified();
            return getLastModified();
        }


        public long getLastModified()
        {
            if (null != _file)
                return _file.lastModified();
            if (_c != null && _c.getCreated() != null)
                return _c.getCreated().getTime();
            return 0;
        }


        public String getContentType()
        {
            if (isCollection())
                return "text/html";
            return PageFlowUtil.getContentTypeFor(_path);
        }


        public long getContentLength()
        {
            if (!isFile() || _file == null)
                return 0;
            return _file.length();
        }


        public String getHref(ViewContext context)
        {
            ActionURL url = context.getActionURL();
            int port = context.getRequest().getServerPort();
            boolean defaultPort = "http".equals(url.getScheme()) && 80 == port || "https".equals(url.getScheme()) && 443 == port;
            String portStr = defaultPort ? "" : ":" + port;
            String href = url.getScheme() + "://" + url.getHost() + portStr + context.getContextPath() + context.getRequest().getServletPath() + PageFlowUtil.encodePath(_path);
            if (isCollection() && !href.endsWith("/"))
                href += "/";
            return href;
        }


        public String getLocalHref(ViewContext context)
        {
            String href = context.getContextPath() + context.getRequest().getServletPath() + PageFlowUtil.encodePath(_path);
            if (isCollection() && !href.endsWith("/"))
                href += "/";
            return href;
        }


        public String getHrefAlternate(ViewContext context)
        {
            return null;
        }


        public String getETag()
        {
            if (null == _etag)
                _etag = "W/\"" + getContentLength() + "-" + getLastModified() + "\"";
            return _etag;
        }


        public boolean canRead(User user)
        {
            return "/".equals(_path) || (getPermissions(user) & ACL.PERM_READ) != 0;
        }


        public boolean canWrite(User user)
        {
            return hasFileSystem() && (getPermissions(user) & ACL.PERM_UPDATE) != 0;
        }


        public boolean canCreate(User user)
        {
            return hasFileSystem() && (getPermissions(user) & ACL.PERM_INSERT) != 0;
        }


        public boolean canDelete(User user)
        {
            if (isWebFolder())
                return false;
            return hasFileSystem() && (getPermissions(user) & ACL.PERM_UPDATE) != 0;
        }


        public boolean canRename(User user)
        {
            if (isWebFolder())
                return false;
            return canCreate(user) && canDelete(user);
        }


        private boolean hasFileSystem()
        {
            return _file != null;
        }


        public int getPermissions(User user)
        {
            return _acl.getPermissions(user);
        }


        public boolean delete(User user)
        {
            if (_file == null || !canDelete(user))
                return false;
            return _file.delete();
        }


        abstract ResourceImpl find(String name);

        /*
         * not part of Resource interface, but used by FtpConnector
         */
        public List<String> getWebFoldersNames(User user)
        {
            return Collections.emptyList();
        }

        public boolean isWebFolder()
        {
            return false;
        }

        public Container getContainer()
        {
            return _c;
        }
    }


    private abstract class FolderResourceImpl extends ResourceImpl
    {
        FolderResourceImpl(String path)
        {
            super(path);
        }

        FolderResourceImpl(String folder, String name)
        {
            super(folder, name);
        }

        @Override
        public boolean isWebFolder()
        {
            return true;
        }

        @Override
        public boolean isCollection()
        {
            return true;
        }
    }


    class WebFolderResource extends FolderResourceImpl
    {
        ArrayList<String> _children = null;

        WebFolderResource(Container c, AttachmentDirectory root)
        {
            super(c.getPath());
            _c = c;
            _acl = c.getAcl();
            _root = root != null ? root.getFileSystemDirectory() : null;
            _file = _root;
        }

        @Override
        public synchronized List<String> getWebFoldersNames(User user)
        {
            if (null ==_children)
            {
                List<Container> list = ContainerManager.getChildren(_c);
                PipeRoot root;
                try
                {
                    root = PipelineService.get().findPipelineRoot(_c);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
                _children = new ArrayList<String>(list.size() + (null==root?0:1));
                for (Container aList : list)
                    _children.add(aList.getName());
                if (null != root)
                    _children.add(PIPELINE_LINK);
                if (wikiDemo)
                    _children.add(WIKI_LINK);
            }
            if (null == user || _children.size() == 0)
                return _children;

            ArrayList<String> ret = new ArrayList<String>();
            for (String name : _children)
            {
                Resource r = lookup(c(this,name));
                if (null != r && r.canRead(user))
                    ret.add(name);
            }
            return ret;
        }

        @Override
        public List<String> listNames()
        {
            Set<String> set = new TreeSet<String>();
            set.addAll(super.listNames());
            set.addAll(getWebFoldersNames(null));
            return new ArrayList<String>(set);
        }

        @Override
        ResourceImpl find(String child)
        {
            for (String name : getWebFoldersNames(null))
            {
                if (name.equalsIgnoreCase(child))
                    return lookupWebFolder(c(this,name));
            }
            if (_root != null)
                return new FileResource(this,child);
            return new UnboundResource(c(this,child));
        }
    }


    private class PipelineFolderResource extends FolderResourceImpl
    {
        PipelineFolderResource(Container c, PipeRoot root)
        {
            super(c.getPath(),PIPELINE_LINK);

            URI uriRoot = (root != null) ? root.getUri(c) : null;
            if (uriRoot != null)
            {
                _acl = org.labkey.api.security.SecurityManager.getACL(c, root.getEntityId());
                _root = canonicalFile(uriRoot);
            }
            _file = _root;
        }

        @Override
        public String getName()
        {
            return PIPELINE_LINK;
        }

        ResourceImpl find(String name)
        {
            if (_root == null)
                return new UnboundResource(c(this,name));
            else
                return new FileResource(this, name);
        }
    }


    /** demo only */
    private class WikiFolderResource extends FolderResourceImpl
    {
        WikiFolderResource(Container c)
        {
            super(c.getPath(),WIKI_LINK);
            _c = c;
            _acl = c.getAcl();
        }

        @Override
        public String getName()
        {
            return WIKI_LINK;
        }

        ResourceImpl find(String name)
        {
            return new WikiResource(this, name);
        }

        public List<String> listNames()
        {
            List<String> names = WikiService.get().getNames(getContainer());
            return names;
        }
    }


    /** demo only */
    private class WikiResource extends ResourceImpl
    {
        WikiFolderResource _folder = null;
        String _name;
        
        WikiResource(WikiFolderResource folder, String name)
        {
            super(folder,name);
            _folder = folder;
            _name = name;
            _c = folder.getContainer();
            _acl = _c.getAcl();
        }

        WikiResource(String path)
        {
            super(path);
        }

        public String getPath()
        {
            return super.getPath();
        }

        public String getName()
        {
            return _name;
        }

        public boolean exists()
        {
            List<String> names = WikiService.get().getNames(getContainer());
            return names.contains(_name);
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isVirtual()
        {
            return true;
        }

        public boolean isFile()
        {
            return exists();
        }

        public InputStream getInputStream() throws IOException
        {
            String html = StringUtils.trimToEmpty(WikiService.get().getHtml(_c, _name, true));
            byte[] buf = html.getBytes("UTF-8");
            return new ByteArrayInputStream(buf);
        }

        public OutputStream getOutputStream() throws IOException
        {
            return new ByteArrayOutputStream();
        }

        public Resource parent()
        {
            return _folder;
        }

        public long getCreation()
        {
            return super.getCreation();
        }

        public long getLastModified()
        {
            return super.getLastModified();
        }

        public String getContentType()
        {
            return "text/html";
        }

        public long getContentLength()
        {
            String html = StringUtils.trimToEmpty(WikiService.get().getHtml(_c, _name, true));
            try
            {
                byte[] buf = html.getBytes("UTF-8");
                return buf.length;
            }
            catch (UnsupportedEncodingException e)
            {
                return 0;
            }
        }

        ResourceImpl find(String name)
        {
            return null;
        }

        public int getPermissions(User user)
        {
            return super.getPermissions(user) & ACL.PERM_READ;
        }
    }


    private class FileResource extends ResourceImpl
    {
        FileResource(FolderResourceImpl folder, String relativePath)
        {
            super(folder, relativePath);
            _acl = folder._acl;
            _file = canonicalFile(new File(folder._root,relativePath));
        }

        FileResource(FileResource folder, String name)
        {
            super(folder, name);
            _acl = folder._acl;
            _file = new File(folder._file,name);
        }

        @Override
        public String getName()
        {
            return _file.getName();
        }

        ResourceImpl find(String name)
        {
            return new FileResource(this, name);
        }
    }


    private class UnboundResource extends ResourceImpl
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
        public int getPermissions(User user)
        {
            return 0;
        }

        ResourceImpl find(String name)
        {
            return new UnboundResource(c(this,name));
        }
    }


    File canonicalFile(URI uri)
    {
        return canonicalFile(new File(uri));
    }


    File canonicalFile(String path)
    {
        return canonicalFile(new File(path));
    }


    File canonicalFile(File f)
    {
        try
        {
            return f.getCanonicalFile();
        }
        catch (IOException x)
        {
            return f;
        }
    }


    String c(Container container, String name)
    {
        return c(container.getPath(), name);
    }


    String c(Resource r, String name)
    {
        return c(r.getPath(), name);
    }


    String c(String path, String name)
    {
        StringBuilder s = new StringBuilder();
        s.append(path, 0, path.length()-(path.endsWith("/")?1:0));
        s.append("/");
        s.append(name, name.startsWith("/")?1:0, name.length());
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
            assertFalse(root.canWrite(user));

            Resource junit = resolver.lookup(c.getPath());
            assertNotNull(junit);
            assertTrue(junit.isCollection());

            String pathTest = junit.getPath() + "/dav";
            Container cTest = ContainerManager.ensureContainer(pathTest);
            ACL aclNone = new ACL();
            aclNone.setPermission(Group.groupGuests, 0);
            aclNone.setPermission(user, ACL.PERM_READ);
            SecurityManager.updateACL(cTest, aclNone);
            Resource rTest = resolver.lookup(pathTest);
            assertNotNull(rTest);
            assertTrue(rTest.canRead(user));
            assertFalse(rTest.canWrite(user));
            assertNotNull(rTest.parent());
            assertTrue(rTest.parent().isCollection());

            List<String> names = resolver.lookup(junit.getPath()).listNames();
            assertFalse(names.contains("webdav"));
            assertTrue(names.contains("dav"));

            ACL aclRead = new ACL();
            aclRead.setPermission(Group.groupGuests,ACL.PERM_READ);
            SecurityManager.updateACL(cTest, aclRead);
            rTest = resolver.lookup(pathTest);
            assertTrue(rTest.canRead(guest));

            ContainerManager.rename(cTest, "webdav");
            String pathNew = junit.getPath() + DavController.SERVLETPATH;
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
