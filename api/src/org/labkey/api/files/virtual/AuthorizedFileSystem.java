package org.labkey.api.files.virtual;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.VirtualFileName;
import org.apache.commons.vfs2.operations.FileOperations;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.jetbrains.annotations.NotNull;
import org.apache.commons.vfs2.FileObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Currently AuthorizedFileSystem supports two modes READ_ONLY and READWRITE
 * Any flavor of fine-grained permissions (e.g. "create, but not delete") must be implemented by the user of the
 * AuthorizedFileSystem
 */
public class AuthorizedFileSystem extends AbstractFileSystem
{
    final FileObject wrappedRoot;
    final FileSystem wrappedFileSystem;
    final boolean allowRead;
    final boolean allowWrite;

    public AuthorizedFileSystem(File f, boolean read, boolean write) throws FileSystemException
    {
        this(VFS.getManager().resolveFile("file://" + f.getPath()), read, write);
        if (!f.isAbsolute())
            throw new FileSystemException(f + " is not absolute");
    }

    public AuthorizedFileSystem(FileObject wrappedFileObjectRoot, boolean read, boolean write) throws FileSystemException
    {
        super(new VirtualFileName(AuthorizedFileSystem.class.getName() + ":", "/", FileType.FOLDER), null, null);
        if (!wrappedFileObjectRoot.isFolder())
            throw new FileSystemException("parentLayer must be a Folder");
        wrappedRoot = wrappedFileObjectRoot;
        wrappedFileSystem = wrappedFileObjectRoot.getFileSystem();
        allowRead = read;
        allowWrite = write;
    }

    @Override
    protected FileObject createFile(AbstractFileName name) throws Exception
    {
        FileObject inner = getParentLayer().resolveFile(name.getPathDecoded(), NameScope.DESCENDENT_OR_SELF);
        return wrapFileObject(inner);
    }

    @Override
    protected void addCapabilities(Collection<Capability> caps)
    {
        // override hasCapability instead
    }

    @Override
    public boolean hasCapability(Capability capability)
    {
        return wrappedFileSystem.hasCapability(capability);
    }

    @Override
    public FileObject getRoot() throws FileSystemException
    {
        return super.getRoot();
    }

    @Override
    public FileName getRootName()
    {
        return super.getRootName();
    }

    @Override
    public String getRootURI()
    {
        return super.getRootURI();
    }

    @Override
    public Object getAttribute(String s) throws FileSystemException
    {
        return super.getAttribute(s);
    }

    @Override
    public void setAttribute(String s, Object o) throws FileSystemException
    {
        super.setAttribute(s, o);
    }

    @Override
    public FileObject resolveFile(FileName fileName) throws FileSystemException
    {
        return resolveFile(fileName.getPath());
    }

    @Override
    public FileObject resolveFile(String s) throws FileSystemException
    {
        String fullPath = wrappedRoot.getName().getPath() + "/" + s;
        FileObject fo = wrappedFileSystem.resolveFile(fullPath);
        return wrapFileObject(fo);
    }

    @Override
    public void addListener(FileObject fileObject, FileListener fileListener)
    {
        super.addListener(fileObject, fileListener);
    }

    @Override
    public void removeListener(FileObject fileObject, FileListener fileListener)
    {
        super.removeListener(fileObject, fileListener);
    }

    @Override
    public void addJunction(String s, FileObject fileObject) throws FileSystemException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeJunction(String s) throws FileSystemException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File replicateFile(FileObject fileObject, FileSelector fileSelector) throws FileSystemException
    {
        return super.replicateFile(fileObject, fileSelector);
    }

    @Override
    public FileSystemOptions getFileSystemOptions()
    {
        return super.getFileSystemOptions();
    }

    @Override
    public FileSystemManager getFileSystemManager()
    {
        return super.getFileSystemManager();
    }

    @Override
    public double getLastModTimeAccuracy()
    {
        return super.getLastModTimeAccuracy();
    }

    _FileObject wrapFileObject(FileObject fo) throws FileSystemException
    {
        if (!wrappedRoot.getName().isDescendent(fo.getName(), NameScope.DESCENDENT_OR_SELF))
            throw new UnauthorizedException();
        var s = wrappedRoot.getName().getRelativeName(fo.getName());
        if (".".equals(s))
            s = "/";
        var name = new VirtualFileName(AuthorizedFileSystem.class.getName() + ":", s, FileType.FILE_OR_FOLDER);
        return new _FileObject(name, fo);
    }

    class _FileObject extends AbstractFileObject<AuthorizedFileSystem>
    {
        final FileObject _fo;

        _FileObject(VirtualFileName name, FileObject fo) throws FileSystemException
        {
            super(name, AuthorizedFileSystem.this);
            _fo = fo;
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof _FileObject other && _fo.equals(other._fo);
        }

        @Override
        protected long doGetContentSize() throws Exception
        {
            return _fo.getContent().getSize();
        }

        @Override
        protected FileType doGetType() throws Exception
        {
            return _fo.getType();
        }

        @Override
        protected String[] doListChildren() throws Exception
        {
            // TODO check common case where _fo is a local file
            var children = _fo.getChildren();
            String[] names = new String[children.length];
            for (int i = 0; i < children.length; i++)
                names[i] = children[i].getName().getBaseName();
            return names;
        }

        void checkWritable()
        {
            if (!allowWrite)
                throw new UnauthorizedException();
        }

        void checkReadable()
        {
            if (!allowRead)
                throw new UnauthorizedException();
        }

        @Override
        public boolean canRenameTo(FileObject newfile)
        {
            return allowWrite && newfile instanceof _FileObject other && _fo.canRenameTo(other._fo);
        }

        @Override
        public void close() throws FileSystemException
        {
            _fo.close();
        }

        @Override
        public void copyFrom(FileObject srcFile, FileSelector selector) throws FileSystemException
        {
            checkWritable();
            _fo.copyFrom(srcFile, selector);
        }

        @Override
        public void createFile() throws FileSystemException
        {
            checkWritable();
            _fo.createFile();
        }

        @Override
        public void createFolder() throws FileSystemException
        {
            checkWritable();
            _fo.createFolder();
        }

        @Override
        public boolean delete() throws FileSystemException
        {
            checkWritable();
            if ("/".equals(getName().getPath()))
                throw new UnauthorizedException();
            return _fo.delete();
        }

        @Override
        public int delete(FileSelector selector) throws FileSystemException
        {
            checkWritable();
            return _fo.delete(selector);
        }

        @Override
        public int deleteAll() throws FileSystemException
        {
            checkWritable();
            return _fo.deleteAll();
        }

        @Override
        public boolean exists() throws FileSystemException
        {
            return allowRead && _fo.exists();
        }

        @Override
        public FileObject[] findFiles(FileSelector selector) throws FileSystemException
        {
            if (!allowRead)
                return new FileObject[0];
            return _fo.findFiles(selector);
        }

        @Override
        public void findFiles(FileSelector selector, boolean depthwise, List<FileObject> selected) throws FileSystemException
        {
            if (!allowRead)
                return;
            _fo.findFiles(selector, depthwise, selected);
        }

        @Override
        public FileObject getChild(String name) throws FileSystemException
        {
            if (!allowRead)
                return null;
            return _fo.getChild(name);
        }

        @Override
        public FileObject[] getChildren() throws FileSystemException
        {
            if (!allowRead)
                return new FileObject[0];
            return _fo.getChildren();
        }

        @Override
        public FileContent getContent() throws FileSystemException
        {
            checkReadable();
            return _fo.getContent();
        }

        @Override
        public FileOperations getFileOperations() throws FileSystemException
        {
            // TODO
            return _fo.getFileOperations();
        }

        @Override
        public FileSystem getFileSystem()
        {
            return AuthorizedFileSystem.this;
        }

        @Override
        public FileObject getParent() throws FileSystemException
        {
            return super.getParent();
        }

        @Override
        public String getPublicURIString()
        {
            // TODO
            return _fo.getPublicURIString();
        }

        @Override
        public FileType getType() throws FileSystemException
        {
            return _fo.getType();
        }

        @Override
        public URI getURI()
        {
            // TODO
            return _fo.getURI();
        }

        @Override
        public Path getPath()
        {
            // TODO this is what we want to discourage!
            return _fo.getPath();
        }

        @Override
        public URL getURL() throws FileSystemException
        {
            // TODO
            return _fo.getURL();
        }

        @Override
        public boolean isAttached()
        {
            return _fo.isAttached();
        }

        @Override
        public boolean isContentOpen()
        {
            return _fo.isContentOpen();
        }

        @Override
        public boolean isExecutable() throws FileSystemException
        {
            return _fo.isExecutable();
        }

        @Override
        public boolean isFile() throws FileSystemException
        {
            return _fo.isFile();
        }

        @Override
        public boolean isFolder() throws FileSystemException
        {
            return _fo.isFolder();
        }

        @Override
        public boolean isHidden() throws FileSystemException
        {
            return _fo.isHidden();
        }

        @Override
        public boolean isReadable() throws FileSystemException
        {
            return allowRead && _fo.isReadable();
        }

        @Override
        public boolean isSymbolicLink() throws FileSystemException
        {
            return _fo.isSymbolicLink();
        }

        @Override
        public boolean isWriteable() throws FileSystemException
        {
            return allowWrite && _fo.isWriteable();
        }

        @Override
        public void moveTo(FileObject destFile) throws FileSystemException
        {
            checkWritable();
            _fo.moveTo(destFile);
        }

        @Override
        public void refresh() throws FileSystemException
        {
            _fo.refresh();
        }

        @Override
        public FileObject resolveFile(String path) throws FileSystemException
        {
            return !allowRead ? null : super.resolveFile(path);
        }

        @Override
        public FileObject resolveFile(String name, NameScope scope) throws FileSystemException
        {
            return !allowRead ? null : super.resolveFile(name, scope);
        }

        @Override
        public boolean setExecutable(boolean executable, boolean ownerOnly) throws FileSystemException
        {
            checkWritable();
            return _fo.setExecutable(executable, ownerOnly);
        }

        @Override
        public boolean setReadable(boolean readable, boolean ownerOnly) throws FileSystemException
        {
            checkWritable();
            return _fo.setReadable(readable, ownerOnly);
        }

        @Override
        public boolean setWritable(boolean writable, boolean ownerOnly) throws FileSystemException
        {
            checkWritable();
            return _fo.setWritable(writable, ownerOnly);
        }

        @Override
        public int compareTo(@NotNull FileObject o)
        {
            checkReadable();
            return _fo.compareTo(o);
        }
    }





    public static class TestCase extends org.junit.Assert
    {
        Path tempDirectoryPath;
        FileObject localRoot;

        @Before
        public void setup() throws IOException
        {
            tempDirectoryPath = FileUtil.createTempDirectory("junit");
            localRoot = VFS.getManager().resolveFile("file://" + tempDirectoryPath);
            assertNotNull(localRoot);
        }

        @Test
        public void nopermission()
        {

        }

        @Test
        public void readonly()
        {

        }

        @Test
        public void readwrite() throws Exception
        {
            AuthorizedFileSystem afs = new AuthorizedFileSystem(localRoot, true, true);
            FileObject root = afs.resolveFile("/");
            assertNotNull(root);
            assertTrue(root.exists());
            try
            {
                root.delete();
                fail("Should not be able to delete root of FileSystem");
            }
            catch (UnauthorizedException fse)
            {
                // expected
            }
            FileObject a = afs.resolveFile("a.txt");
            a.createFile();
            assertTrue(a.isFile());
            FileObject x = afs.resolveFile("a.txt");
            assertEquals(a,x);
            assertTrue(x.exists());
            x = afs.resolveFile("/a.txt");
            assertEquals(a,x);
            assertTrue(x.exists());
            assertTrue(a.delete());
            assertFalse(a.exists());
            assertFalse(x.exists());

            try
            {
                afs.resolveFile("../a.txt");
                fail("all files outside '/' are unauthorized");
            }
            catch (UnauthorizedException ue)
            {
                // expected
            }
        }

        @After
        public void cleanup() throws IOException
        {
            FileUtil.deleteDir(tempDirectoryPath);
        }
    }
}

/*
 TODO

[ ] get clear on proper time to use encoded or decoded paths
[ ] Do we need to support URI methods?

*/