package org.labkey.api.files.virtual;

import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileContentInfo;
import org.apache.commons.vfs2.FileListener;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.RandomAccessContent;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.VirtualFileName;
import org.apache.commons.vfs2.operations.FileOperations;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.apache.commons.vfs2.util.RandomAccessMode;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Currently AuthorizedFileSystem supports two modes READ_ONLY and READWRITE
 * Any flavor of fine-grained permissions (e.g. "create, but not delete") must be implemented by the user of the
 * AuthorizedFileSystem
 */
public class AuthorizedFileSystem extends AbstractFileSystem
{
    final static String SCHEME = AuthorizedFileSystem.class.getName() + ":";
    final FileObject _rootInnerFileObject;
    final FileObject _rootWrapperFileObject;
    final FileSystem _wrappedFileSystem;
    final boolean _allowList = true;
    final boolean _allowRead;
    final boolean _allowWrite;
    final boolean _allowDeleteRoot;

    private static final Logger LOG = LogHelper.getLogger(AuthorizedFileSystem.class, "Virtual file system");

    public enum Mode
    {
        Read(true, false),
        Write(false, true),
        Read_Write(true, true);

        private final boolean _canRead;
        private final boolean _canWrite;

        Mode(boolean canRead, boolean canWrite)
        {
            _canRead = canRead;
            _canWrite = canWrite;
        }

        boolean canRead() {return _canRead;}
        boolean canWrite() {return _canWrite;}
    }

    public static AuthorizedFileSystem create(File f, Mode mode)
    {
        return create(f, mode.canRead(), mode.canWrite());
    }

    public static AuthorizedFileSystem create(File f, boolean read, boolean write)
    {
        if (!f.isAbsolute())
            throw new IllegalArgumentException(f + " is not absolute");
        try
        {
            return new AuthorizedFileSystem(VFS.getManager().resolveFile(f.getAbsolutePath()), read, write, false);
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public static AuthorizedFileSystem create(Path path, Mode mode)
    {
        return create(path, mode.canRead(), mode.canWrite());
    }

    public static AuthorizedFileSystem create(Path path, boolean read, boolean write)
    {
        if (!path.isAbsolute())
            throw new IllegalArgumentException(path + " is not absolute");
        try
        {
            return new AuthorizedFileSystem(VFS.getManager().resolveFile(path.toAbsolutePath().toString()), read, write, false);
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    // same as create(), but allows the root to be deleted.  This is useful for temp subdirectories.
    public static AuthorizedFileSystem createTemp(File f)
    {
        if (!f.isAbsolute())
            throw new IllegalArgumentException(f + " is not absolute");
        try
        {
            return new AuthorizedFileSystem(VFS.getManager().resolveFile(f.toURI()), true, true, true);
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    /** This is a helper for transitioning from File->FileObject
     *<br>
     * a) In experiment land we often have free floating file URIs with no associated file system "root directory".  The
     *    solution for now is to wrap these files with an AFS scoped to its parent directory.
     * b) We often have a list of files that may be colocated, and it makes sense to create one (or a few) AFS that they share.
     *<br>
     * Given a collection of File objects that are likely in the same directory, convert them to FileObject object that are
     * each scoped to their own parent directory.
     * <br>
     * We should try to minimize usage of this method, however, it is useful while migrating the codebase.
     */
    public static List<FileObject> convertToFileObjects(List<File> files)
    {
        try
        {
            Map<File, AuthorizedFileSystem> map = new HashMap<>();
            List<FileObject> ret = new ArrayList<>(files.size());
            for (File file : files)
            {
                File parent = file.getParentFile();
                AuthorizedFileSystem fs = map.computeIfAbsent(parent, key -> AuthorizedFileSystem.create(parent, true, true));
                ret.add(fs.resolveFile(file.getName()));
            }
            return ret;
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    /* See note above.  We should try to minimize usage of this method, however, it is useful while migrating the codebase. */
    public static FileObject convertToFileObject(File file)
    {
        try
        {
            if (null == file)
                return null;
            File parent = file.getParentFile();
            return AuthorizedFileSystem.create(parent, true, true).getRoot().resolveFile(file.getName());
        }
        catch (FileSystemException fse)
        {
            throw UnexpectedException.wrap(fse);
        }
    }


    public static AuthorizedFileSystem createReadOnly(AuthorizedFileSystem src)
    {
        if (src._allowRead && !src._allowWrite)
            return src;
        return new AuthorizedFileSystem(src._rootInnerFileObject, true, false, false);
    }


    public static AuthorizedFileSystem createReadWrite(AuthorizedFileSystem src)
    {
        if (src._allowRead && src._allowWrite)
            return src;
        return new AuthorizedFileSystem(src._rootInnerFileObject, true, true, false);
    }


    private AuthorizedFileSystem(FileObject wrappedFileObjectRoot, boolean read, boolean write, boolean allowDeleteRoot)
    {
        super(new VirtualFileName(SCHEME, "/", FileType.FOLDER), null, null);

        // NOTE: wrappedFileObjectRoot.getPath() calls wrappedFileObjectRoot.getURI() which does not seem to handle unicode very well???
        LOG.debug("AuthorizedFileSystem(" + wrappedFileObjectRoot.getName().getPath() + "," + (read?"r":"") + (write?"w":"")+")");
        try
        {
            if (wrappedFileObjectRoot.isFile())
                throw new IllegalArgumentException("parentLayer must be a Folder");
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
        _rootInnerFileObject = wrappedFileObjectRoot;
        _rootWrapperFileObject = wrapFileObject(_rootInnerFileObject);
        _wrappedFileSystem = wrappedFileObjectRoot.getFileSystem();
        _allowRead = read;
        _allowWrite = write;
        _allowDeleteRoot = allowDeleteRoot;
    }

    public FileObject getInnerFileObject()
    {
        return _rootInnerFileObject;
    }

    void checkListable()
    {
        if (!_allowList)
            throw new UnauthorizedException();
    }

    void checkReadable()
    {
        if (!_allowRead)
            throw new UnauthorizedException();
    }

    void checkWritable()
    {
        if (!_allowWrite)
            throw new UnauthorizedException();
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
        switch (capability)
        {
            case READ_CONTENT:
            case RANDOM_ACCESS_READ:
                if (!_allowRead)
                    return false;
                break;

            case WRITE_CONTENT:
            case RANDOM_ACCESS_SET_LENGTH:
            case RANDOM_ACCESS_WRITE:
            case APPEND_CONTENT:
            case SET_LAST_MODIFIED_FILE:
            case SET_LAST_MODIFIED_FOLDER:
            case CREATE:
            case DELETE:
            case RENAME:
                if (!_allowWrite)
                    return false;
                break;

            case ATTRIBUTES:
            case LAST_MODIFIED:
            case GET_LAST_MODIFIED:
            case GET_TYPE:
            case LIST_CHILDREN:
                if (!_allowList)
                    return false;
                break;

            case JUNCTIONS:
                return false;

            case DIRECTORY_READ_CONTENT:
            case SIGNING:
            case URI:
            case FS_ATTRIBUTES:
            case MANIFEST_ATTRIBUTES:
            case DISPATCHER:
            case COMPRESS:
            case VIRTUAL:
                break;
        }
        return _wrappedFileSystem.hasCapability(capability);
    }

    @Override
    public FileObject getRoot()
    {
        return _rootWrapperFileObject;
    }

    @Override
    public FileName getRootName()
    {
        return _rootWrapperFileObject.getName();
    }

    @Override
    public String getRootURI()
    {
        throw new UnsupportedOperationException();
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
        String fullPath = _rootInnerFileObject.getName().getPath() + "/" + s;
        FileObject fo = _wrappedFileSystem.resolveFile(fullPath);
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

    class _FileContent implements FileContent
    {
        final FileObject _fo;
        final FileContent _fc;

        _FileContent(FileObject fo) throws FileSystemException
        {
            _fo = fo;
            _fc = fo.getContent();
        }

        @Override
        public void close() throws FileSystemException
        {
            _fc.close();
        }

        @Override
        public Object getAttribute(String attrName) throws FileSystemException
        {
            checkListable();
            return _fc.getAttribute(attrName);
        }

        @Override
        public String[] getAttributeNames() throws FileSystemException
        {
            checkListable();
            return _fc.getAttributeNames();
        }

        @Override
        public Map<String, Object> getAttributes() throws FileSystemException
        {
            checkListable();
            return _fc.getAttributes();
        }

        @Override
        public Certificate[] getCertificates() throws FileSystemException
        {
            checkListable();
            return _fc.getCertificates();
        }

        @Override
        public FileContentInfo getContentInfo() throws FileSystemException
        {
            checkListable();
            return _fc.getContentInfo();
        }

        @Override
        public FileObject getFile()
        {
            return _fc.getFile();
        }

        @Override
        public InputStream getInputStream() throws FileSystemException
        {
            checkReadable();
            return _fc.getInputStream();
        }

        @Override
        public InputStream getInputStream(int bufferSize) throws FileSystemException
        {
            checkReadable();
            return _fc.getInputStream(bufferSize);
        }

        @Override
        public long getLastModifiedTime() throws FileSystemException
        {
            checkListable();
            return _fc.getLastModifiedTime();
        }

        @Override
        public OutputStream getOutputStream() throws FileSystemException
        {
            checkWritable();
            return _fc.getOutputStream();
        }

        @Override
        public OutputStream getOutputStream(boolean bAppend) throws FileSystemException
        {
            checkWritable();
            return _fc.getOutputStream(bAppend);
        }

        @Override
        public OutputStream getOutputStream(boolean bAppend, int bufferSize) throws FileSystemException
        {
            checkWritable();
            return _fc.getOutputStream(bAppend, bufferSize);
        }

        @Override
        public OutputStream getOutputStream(int bufferSize) throws FileSystemException
        {
            checkWritable();
            return _fc.getOutputStream(bufferSize);
        }

        @Override
        public RandomAccessContent getRandomAccessContent(RandomAccessMode mode) throws FileSystemException
        {
            if (mode.requestRead())
                checkReadable();
            if (mode.requestWrite())
                checkWritable();
            return _fc.getRandomAccessContent(mode);
        }

        @Override
        public long getSize() throws FileSystemException
        {
            checkListable();
            return _fc.getSize();
        }

        @Override
        public boolean hasAttribute(String attrName) throws FileSystemException
        {
            checkListable();
            return _fc.hasAttribute(attrName);
        }

        @Override
        public boolean isOpen()
        {
            return _fc.isOpen();
        }

        @Override
        public void removeAttribute(String attrName) throws FileSystemException
        {
            checkWritable();
            _fc.removeAttribute(attrName);
        }

        @Override
        public void setAttribute(String attrName, Object value) throws FileSystemException
        {
            checkWritable();
            _fc.setAttribute(attrName, value);
        }

        @Override
        public void setLastModifiedTime(long modTime) throws FileSystemException
        {
            checkWritable();
            _fc.setLastModifiedTime(modTime);
        }

        @Override
        public long write(FileObject file) throws IOException
        {
            checkReadable();
            return _fc.write(file);
        }

        @Override
        public long write(FileContent output) throws IOException
        {
            checkReadable();
            return _fc.write(output);
        }

        @Override
        public long write(OutputStream output) throws IOException
        {
            checkReadable();
            return _fc.write(output);
        }

        @Override
        public long write(OutputStream output, int bufferSize) throws IOException
        {
            checkReadable();
            return _fc.write(output, bufferSize);
        }
    }


    _FileObject wrapFileObject(FileObject fo)
    {
        if (!_rootInnerFileObject.getName().isDescendent(fo.getName(), NameScope.DESCENDENT_OR_SELF))
            throw new UnauthorizedException();
        String relative;
        try
        {
            // this should not actually throw
            relative = _rootInnerFileObject.getName().getRelativeName(fo.getName());
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
        String absPath;
        if (".".equals(relative))
            absPath = "/";
        else
            absPath = "/" + relative;
        var name = new VirtualFileName(SCHEME, absPath, FileType.FILE_OR_FOLDER);
        return new _FileObject(name, fo);
    }

    /*
     * AbstractFileObject maybe isn't the right base class, we don't want it to do any caching at all.
     * e.g. see doGetType()
     */
    class _FileObject extends AbstractFileObject<AuthorizedFileSystem>
    {
        final FileObject _fo;

        _FileObject(VirtualFileName name, FileObject fo)
        {
            super(name, AuthorizedFileSystem.this);
            _fo = fo;
        }

        @Override
        protected long doGetContentSize() throws Exception
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileType getType() throws FileSystemException
        {
            return _fo.getType();
        }

        @Override
        protected FileType doGetType() throws Exception
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject[] getChildren() throws FileSystemException
        {
            if (!_allowList)
                return new FileObject[0];
            refresh();
            FileObject[] children = _fo.getChildren();
            FileObject[] ret = new FileObject[children.length];
            for (int i=0 ; i < children.length ; i++)
                ret[i] = wrapFileObject(children[i]);
            return ret;
        }

        @Override
        protected String[] doListChildren() throws Exception
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canRenameTo(FileObject newfile)
        {
            return _allowWrite && newfile instanceof _FileObject other && _fo.canRenameTo(other._fo);
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
            refresh();
        }

        @Override
        public void createFolder() throws FileSystemException
        {
            checkWritable();
            _fo.createFolder();
            refresh();
        }

        @Override
        public boolean delete() throws FileSystemException
        {
            checkWritable();
            if (!_allowDeleteRoot && "/".equals(getName().getPath()))
                throw new UnauthorizedException();
            var ret = _fo.delete();
            refresh();
            return ret;
        }

        @Override
        public int delete(FileSelector selector) throws FileSystemException
        {
            checkWritable();
            var ret = _fo.delete(selector);
            refresh();
            return ret;
        }

        @Override
        public int deleteAll() throws FileSystemException
        {
            checkWritable();
            var ret = _fo.deleteAll();
            refresh();
            return ret;
        }

        @Override
        public boolean exists() throws FileSystemException
        {
            return _allowRead && _fo.exists();
        }

        @Override
        public FileObject[] findFiles(FileSelector selector) throws FileSystemException
        {
            if (!_allowRead)
                return new FileObject[0];
            var array = _fo.findFiles(selector);
            FileObject[] ret = new FileObject[array.length];
            for (int i=0 ; i < array.length ; i++)
                ret[i] = wrapFileObject(array[i]);
            return ret;
        }

        @Override
        public void findFiles(FileSelector selector, boolean depthwise, List<FileObject> selected) throws FileSystemException
        {
            if (!_allowRead)
                return;
            ArrayList<FileObject> found = new ArrayList<>();
            _fo.findFiles(selector, depthwise, found);
            found.forEach(f -> selected.add(wrapFileObject(f)));
        }

        @Override
        public FileObject getChild(String name) throws FileSystemException
        {
            if (!_allowRead)
                return null;
            return wrapFileObject(_fo.getChild(name));
        }

        @Override
        public FileContent getContent() throws FileSystemException
        {
            return new _FileContent(_fo);
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
            return _fo.getPublicURIString();
        }

        @Override
        public URI getURI()
        {
            return _fo.getURI();
        }

        @Override
        public Path getPath()
        {
            return _fo.getPath();
        }

        @Override
        public URL getURL() throws FileSystemException
        {
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
            return _allowRead && _fo.isReadable();
        }

        @Override
        public boolean isSymbolicLink() throws FileSystemException
        {
            return _fo.isSymbolicLink();
        }

        @Override
        public boolean isWriteable() throws FileSystemException
        {
            return _allowWrite && _fo.isWriteable();
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
            super.refresh();
            _fo.refresh();
        }

        @Override
        public FileObject resolveFile(String path) throws FileSystemException
        {
            if (path.startsWith("/"))
                return AuthorizedFileSystem.this.resolveFile(path);
            else
                return wrapFileObject(_fo.resolveFile(path));
        }

        @Override
        public FileObject resolveFile(String name, NameScope scope) throws FileSystemException
        {
            return wrapFileObject(_fo.resolveFile(name, scope));
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
        public int compareTo(@NotNull FileObject other)
        {
            if (!(other instanceof _FileObject otherFO))
                throw new IllegalArgumentException();
            return _fo.compareTo(otherFO._fo);
        }

        @Override
        public int hashCode()
        {
            return _fo.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof _FileObject other && _fo.equals(other._fo);
        }
    }


    public static class TestCase extends org.junit.Assert
    {
        @Test
        public void nopermission()
        {

        }

        @Test
        public void readonly()
        {

        }

        @Test
        public void create_delete() throws Exception
        {
            FileObject root = FileUtil.createTempDirectoryFileObject(AuthorizedFileSystem.class.getName());
            assertTrue(root.getFileSystem() instanceof AuthorizedFileSystem);
            var readOnlyRoot = createReadOnly((AuthorizedFileSystem)root.getFileSystem()).getRoot();

            try
            {
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

                FileObject aRO = readOnlyRoot.resolveFile("a.txt");
                assertFalse(aRO.exists());
                try
                {
                    aRO.createFile();
                    fail("shouldn't be able to create file in readonly file system");
                }
                catch (UnauthorizedException fse)
                {
                    // pass
                }

                var a = root.resolveFile("a.txt");
                assertFalse(a.exists());
                a.createFile();
                assertTrue(a.isFile());
                FileObject x = root.resolveFile("a.txt");
                assertEquals(a, x);
                assertTrue(x.exists());
                x = root.resolveFile("/a.txt");
                assertEquals(a, x);
                assertTrue(x.exists());
                try
                {
                    aRO.delete();
                    fail("shouldn't be able to delete file in readonly file system");
                }
                catch (UnauthorizedException ue)
                {
                    // pass
                }
                assertTrue(a.delete());
                assertFalse(a.exists());
                assertFalse(x.exists());
                assertFalse(aRO.exists());

                try
                {
                    root.resolveFile("../a.txt");
                    fail("all files outside '/' are unauthorized");
                }
                catch (UnauthorizedException ue)
                {
                    // expected
                }
            }
            finally
            {
                FileUtil.deleteTempDirectoryFileObject(root);
            }
        }

        @Test
        public void read_write() throws Exception
        {
            FileObject root = FileUtil.createTempDirectoryFileObject(AuthorizedFileSystem.class.getName());
            var readOnlyRoot = createReadOnly((AuthorizedFileSystem)root.getFileSystem()).getRoot();
            try
            {
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

                FileObject a = root.resolveFile("a.txt");
                a.createFile();
                assertTrue(a.isFile());

                try (FileContent contentRW = a.getContent();
                    OutputStream os = contentRW.getOutputStream())
                {
                    Writer w = new OutputStreamWriter(os, Charset.defaultCharset());
                    w.write("Hello World");
                    w.close();
                }

                FileObject aRO = readOnlyRoot.resolveFile("a.txt");
                FileContent contentRO = aRO.getContent();
                try (var is = contentRO.getInputStream())
                {
                    // success, we can read
                }
                assertEquals("Hello World", contentRO.getString(Charset.defaultCharset()));
                try
                {
                    var os = contentRO.getOutputStream();
                    fail("shouldn't be able to write");
                }
                catch (UnauthorizedException ue)
                {
                    // pass
                }
            }
            finally
            {
                FileUtil.deleteTempDirectoryFileObject(root);
            }
        }

        @Test
        public void testFileName() throws Exception
        {
            // just to make sure I understand how FileName works
            FileObject fo = VFS.getManager().resolveFile("/a/b/file%20name.txt");
            assertNotNull(fo);

            FileName fn = fo.getName();
            assertNotNull(fn);
            assertEquals("file name.txt", fn.getBaseName());
            assertEquals("file:///a/b/file name.txt", fn.toString());
            assertEquals("txt", fn.getExtension());

            Path path = fo.getPath();
            assertEquals("/a/b/file name.txt", path.toString());

            assertEquals(fo.getURI().toString(), fo.getURL().toString());
        }

        @Test
        public void testSet() throws Exception
        {
            // we put files in maps and sets, test that this works for _FileObject

            // same file from different roots, and different files same root
            FileObject a = AuthorizedFileSystem.create(new File("/a/"),false,false).getRoot();
            FileObject c1 = a.resolveFile("b/c.txt");
            assertNotNull(c1);
            FileObject b = AuthorizedFileSystem.create(new File("/a/b/"),false,false).getRoot();
            FileObject c2 = b.resolveFile("c.txt");
            assertNotNull(c2);
            FileObject d1 =  b.resolveFile("d.txt");
            assertNotNull(d1);
            FileObject d2 =  b.resolveFile("c/d.txt");
            assertNotNull(d2);

            assertEquals(c1, c2);
            assertEquals(0, c1.compareTo(c2));
            assertEquals(c1.hashCode(), c2.hashCode());
            assertNotEquals(d1, d2);

            HashSet<FileObject> s = new HashSet<>();
            s.add(c1);
            assertTrue(s.contains(c2));
            s.add(d1);
            assertTrue(s.contains(d1));
            assertFalse(s.contains(d2));
        }

        @Test
        public void bug() throws Exception
        {
            java.nio.file.Path nioPath;
/*
            try
            {
                // TLDR; resolveFile() tries to infer escaped/not escaped.  Blows up in an unexpected place when UNICODE chars are involved.
                // This is example is wrong because resolveFile() expects an encoded path.
                // This _usually_ works because resolveFile() looks for '%' to decide that the path does not need to be decoded.
                // However, this string has both a % (and a sequence that looks like a valid escape) AND unicode chars which are not valid in an escaped string
                // resolveFile() lets this through, but blows up when getPath() tries to use URI(String str) on this escaped string with UNICODE
                File f = new File("/lk/develop/build/deploy/files/FileRootTestProject1☃~!@$&()_+{}-=[],.%23äöü/Subfolder1/SubSubfolder/@files");
                // resolveFile will always call
                FileObject localFile = VFS.getManager().resolveFile(f.getAbsolutePath());
                nioPath = localFile.getPath();
                fail("expect URISyntaxException");
            }
            catch (Exception e)
            {
                // pass
            }

            try
            {
                // I think this should be work
                // f.toURI() _SHOULD_ create a URL that VFS thinks is fine
                // NOTE: alas no. File.toURI() will encode the %, but passes the unicode chars, again this blows up in URI(String path, Strings scheme)
                File f = new File("/lk/develop/build/deploy/files/FileRootTestProject1☃~!@$&()_+{}-=[],.%23äöü/Subfolder1/SubSubfolder/@files");
                FileObject localFile = VFS.getManager().resolveFile(f.toURI());
                nioPath = localFile.getPath();
                fail("This throws in 2.7.0");
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
*/
            try
            {
                // HOW about if we do the encoding ourselves
                File f = new File("/lk/develop/build/deploy/files/FileRootTestProject1☃~!@$&()_+{}-=[],.%23äöü/Subfolder1/SubSubfolder/@files");
                var uri = FileUtil.createUri(f.getAbsolutePath());
                assertFalse(uri.toString().contains("☃"));
                assertTrue(f.getPath().equals(uri.getPath()));
                FileObject localFile = VFS.getManager().resolveFile(uri);
                nioPath = localFile.getPath();
            }
            catch (Exception e)
            {
                fail("This should work");
            }
        }
    }
}

/*
 TODO
[ ] exp.data.datafileurl is a problem.  We really need to be able to map those back to a current PipeRoot
[ ] use AbstractFileSystem.close()?
[ ] There is way too much metadata caching going on.  NOTE FileInfo in FileSystemResource teies to solve the same
    problem of excessive calls to isFile(), exists() etc.  So there are cases where we want the caching.

CONSIDER

[ ] fine-grained permissions list/read/insert/update/delete
[ ] handling declared exception FileSystemException is annoying

NOTES
To find File usages that are candidates for conversion look for
- File (case-sensitive whole word), especially in interfaces and Controller classes.
- getPath().toFile()
- FileUtil.appendName()
- The JDK implementation of LocalFile seems to have problems with Unicode (e.g. fileobject.getPath() blows up!), use new File(fileobject.getName().getPath()) instead?

*/