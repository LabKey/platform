package org.labkey.vfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Arrays;
import java.util.List;


/**
 * As of the initial commit of the FileSystemLike, this class is only used for testing out the interfaces.
 * Eventually we may integrate this class as a wrapper for cloud resources or Folder export/import.
 */
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
    public URI getURI(FileLike fo)
    {
        return ((_FileLike)fo).vfs.getURI();
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
            var encRelativePath = StringUtils.strip(toURIPath(path),"/");
            var vfsPath = vfsRoot.resolveFile(encRelativePath);
            return new _FileLike(path, vfsPath);
        }
        catch (FileSystemException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    @JsonSerialize(using = FileLike.FileLikeSerializer.class)
    @JsonDeserialize(using = FileLike.FileLikeDeserializer.class)
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
        public @NotNull List<FileLike> getChildren()
        {
            if (!canList())
                throw new UnauthorizedException();
            try
            {
                FileObject[] array = vfs.getChildren();
                List<FileLike> list = Arrays.stream(array).map(fo -> (FileLike) new _FileLike(path.append(fo.getName().getBaseName()), fo)).toList();
                return list;
            }
            catch (FileSystemException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public void _createFile() throws IOException
        {
            vfs.createFile();
        }

        @Override
        public void delete() throws IOException
        {
            if (!canWriteFiles())
                throw new UnauthorizedException();
            if (this.getPath().isEmpty() && !canDeleteRoot())
                throw new UnauthorizedException();
            vfs.delete();
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
            if (!canWriteFiles())
                throw new UnauthorizedException();
            return getContent().getOutputStream();
        }

        @Override
        public InputStream openInputStream() throws IOException
        {
            if (!canReadFiles())
                throw new UnauthorizedException();
            return getContent().getInputStream();
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
