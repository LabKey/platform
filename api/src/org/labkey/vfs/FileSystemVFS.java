package org.labkey.vfs;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;

public class FileSystemVFS extends AbstractFileSystemLike
{
    final FileObject vfsRoot;
    final _FileLike root;

    FileSystemVFS(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        super(uri, canRead, canWrite, canDeleteRoot);
        try
        {
            vfsRoot = VFS.getManager().resolveFile(uri);
            root = new _FileLike(Path.rootPath, vfsRoot);
        }
        catch (FileSystemException e)
        {
            throw new ConfigurationException("VFS could not be configured", e);
        }
    }

    @Override
    public FileLike getRoot()
    {
        return root;
    }

    @Override
    public FileLike resolveFile(Path path)
    {
        try
        {
            path = path.absolute().normalize();
            if (null == path)
                throw new IllegalArgumentException("Path could not be resolved");
            var vfsPath = vfsRoot.resolveFile(toURIPath(path));
            return new _FileLike(path, vfsPath);
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    class _FileLike extends AbstractFileLike
    {
        final FileObject vfs;
        FileContent content;

        _FileLike(Path path, FileObject vfs)
        {
            super(path);
            this.vfs = vfs;
        }

        @Override
        public FileSystemLike getFileSystem()
        {
            return FileSystemVFS.this;
        }

        @Override
        public @NotNull Collection<FileLike> getChildren()
        {
            throw new NotImplementedException();
        }

        @Override
        public void _createFile() throws IOException
        {
            vfs.createFile();
        }

        @Override
        public void _mkdir() throws IOException
        {
            if (!vfs.getParent().isFolder())
                throw new IOException("Parent is not a folder");
            vfs.createFolder();
        }

        @Override
        public void _mkdirs() throws IOException
        {
            vfs.createFolder();
        }

        private FileContent getContent()
        {
            try
            {
                if (null == content)
                    content = vfs.getContent();
                return content;
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public boolean exists()
        {
            try
            {
                return vfs.exists();
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public boolean isDirectory()
        {
            try
            {
                return vfs.isFolder();
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public boolean isFile()
        {
            try
            {
                return vfs.isFile();
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public long getSize()
        {
            try
            {
                return getContent().getSize();
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public OutputStream openOutputStream() throws IOException
        {
            if (!canWrite)
                throw new UnauthorizedException();
            return null;
        }

        @Override
        public InputStream openInputStream() throws IOException
        {
            if (!canRead)
                throw new UnauthorizedException();
            return null;
        }

        @Override
        public void refresh()
        {
            try
            {
                vfs.refresh();
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public int compareTo(@NotNull FileLike o)
        {
            if (!(o instanceof _FileLike fl))
                throw new ClassCastException();
            return vfs.getName().compareTo(fl.vfs.getName());
        }

        @Override
        public int hashCode()
        {
            return vfs.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof _FileLike other))
                return false;
            return vfs.getName().equals(other.vfs.getName());
        }
    }
}
