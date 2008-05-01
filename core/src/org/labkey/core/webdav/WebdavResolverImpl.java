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
import org.labkey.core.ftp.FtpController;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
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
        if (!fullPath.startsWith("/"))
            fullPath = "/" + fullPath;
        if (fullPath.endsWith("/"))
            fullPath = fullPath.substring(0,fullPath.length()-1);
        fullPath = FileUtil.normalize(fullPath);

        // find first folder that claims this path
        String path = fullPath;
        FolderResourceImpl folder;
        do
        {
            folder = lookupWebFolder(path);
            if (folder != null)
                break;
            path = path.substring(0,path.lastIndexOf("/"));
        } while (path.length() > 0);
        if (folder == null)
            return null;

        String relPath = fullPath.substring(path.length());
        if (relPath.length() == 0)
            return folder;

        // don't return FileResource if there is no file system
        if (folder._root != null)
            return new FileResource(folder, relPath);
        return null;
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
                        invalidate(getParentPath(c) + "/" + oldName, true);
                        invalidate(getParentPath(c), false);
                        break;
                    }
                    case Parent:
                    {
                        Container oldParent = (Container)pce.getOldValue();
                        invalidate(c.getPath(), true);
                        invalidate(getParentPath(c), false);
                        invalidate(oldParent.getPath() + "/" + c.getName(), true);
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
            {
                if (!path.endsWith("/"))
                    path += "/";
                removeUsingPrefix(path);
            }
        }
    }

    FolderCache _folderCache = new FolderCache();
    

    private FolderResourceImpl lookupWebFolder(String folder)
    {
        boolean isPipelineLink = false;
        if (folder.endsWith("/"))
            folder = folder.substring(0,folder.length()-1);
        if (folder.endsWith("/" + PIPELINE_LINK))
        {
            isPipelineLink = true;
            folder = folder.substring(0, folder.length()- PIPELINE_LINK.length()-1);
        }

        Container c = ContainerManager.getForPath(folder);
        if (null == c)
            return null;

        // normalize case of folder 
        folder = isPipelineLink ? c.getPath() + "/" + PIPELINE_LINK : c.getPath();

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


    public class ResourceImpl implements Resource
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
            return _file != null;
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

        public String[] listNames()
        {
            if (!isCollection())
                return new String[0];
            Set<String> set = new TreeSet<String>();
            if (_file != null && _file.isDirectory())
            {
                File[] list = _file.listFiles();
                if (null != list)
                {
                    for (File f: list)
                        set.add(f.getName());
                }
            }
            String[] subfolders = getWebFoldersNames(null);
            set.addAll(Arrays.asList(subfolders));
            return set.toArray(new String[set.size()]);
        }

        public Resource[] list()
        {
            // UNDONE optimize
            String[] names = listNames();
            Resource[] infos = new ResourceImpl[names.length];
            for (int i=0 ; i<names.length ; i++)
                infos[i] = lookup(_path + (_path.endsWith("/") ? "" : "/") + names[i]);
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
            if (!isFile())
                return "text/html;charset=UTF-8";
            return PageFlowUtil.getContentTypeFor(_path);
        }

        public long getContentLength()
        {
            if (!isFile())
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
            String href = context.getContextPath() + "/webdav" + PageFlowUtil.encodePath(_path);
            if (isCollection() && !href.endsWith("/"))
                href += "/";
            return href;
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

        /*
         * not part of Resource interface, but used by FtpConnector
         */
        public String[] getWebFoldersNames(User user)
        {
            return new String[0];
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

    private class FolderResourceImpl extends ResourceImpl
    {
        FolderResourceImpl(String path)
        {
            super(path);
        }

        @Override
        public boolean isWebFolder()
        {
            return true;
        }
    }


    class WebFolderResource extends FolderResourceImpl
    {
        String[] _children = null;
        
        WebFolderResource(Container c, AttachmentDirectory root)
        {
            super(c.getPath());
            _c = c;
            _acl = c.getAcl();
            _root = root != null ? root.getFileSystemDirectory() : null;
            _file = _root;
        }

        @Override
        public synchronized String[] getWebFoldersNames(User user)
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
                _children = new String[list.size() + (null==root?0:1)];
                for (int i=0 ; i < list.size() ; i++)
                    _children[i] = list.get(i).getName();
                if (null != root)
                    _children[_children.length-1] = PIPELINE_LINK;
            }
            if (null == user || _children.length == 0)
                return _children;

            ArrayList<String> ret = new ArrayList<String>();
            for (String name : _children)
            {
                Resource r = lookup(getPath() + "/" + name);
                if (null != r && r.canRead(user))
                    ret.add(name);
            }
            return ret.toArray(new String[ret.size()]);
        }

        @Override
        public boolean isCollection()
        {
            return true;
        }
    }


    private class PipelineFolderResource extends FolderResourceImpl
    {
        PipelineFolderResource(Container c, PipeRoot root)
        {
            super(c.getPath() + "/" + PIPELINE_LINK);

            URI uriRoot = (root != null) ? root.getUri(c) : null;
            if (uriRoot != null)
            {
                _acl = org.labkey.api.security.SecurityManager.getACL(c, root.getEntityId());
                _root = canonicalFile(uriRoot);
            }
            _file = _root;
        }

        @Override
        public boolean isCollection()
        {
            return true;
        }

        @Override
        public String getName()
        {
            return PIPELINE_LINK;
        }
    }


    private class FileResource extends ResourceImpl
    {
        FileResource(FolderResourceImpl folder, String relativePath)
        {
            super(folder.getPath() + (relativePath.startsWith("/") ? "" : "/") + relativePath);
            _acl = folder._acl;
            _file = canonicalFile(folder._root.getPath() + relativePath);
        }

        @Override
        public String getName()
        {
            return _file.getName();
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

            String[] names = resolver.lookup(junit.getPath()).listNames();
            boolean foundWebdav = false;
            boolean foundDav = false;
            for (String name : names)
            {
                if (name.equals("webdav"))
                    foundWebdav = true;
                if (name.equals("dav"))
                    foundDav = true;
            }
            assertFalse(foundWebdav);
            assertTrue(foundDav);
            
            ACL aclRead = new ACL();
            aclRead.setPermission(Group.groupGuests,ACL.PERM_READ);
            SecurityManager.updateACL(cTest, aclRead);
            rTest = resolver.lookup(pathTest);
            assertTrue(rTest.canRead(guest));

            ContainerManager.rename(cTest, "webdav");
            String pathNew = junit.getPath() + "/webdav"; 
//            Container cTestNew = ContainerManager.getForPath(pathNew);
            assertNull(resolver.lookup(pathTest));
            assertNotNull(resolver.lookup(pathNew));

            names = resolver.lookup(junit.getPath()).listNames();
            foundWebdav = false;
            foundDav = false;
            for (String name : names)
            {
                if (name.equals("webdav"))
                    foundWebdav = true;
                if (name.equals("dav"))
                    foundDav = true;
            }
            assertTrue(foundWebdav);
            assertFalse(foundDav);

            Resource rNotFound = resolver.lookup("/NotFound/" + GUID.makeHash());
            assertNull(rNotFound);
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