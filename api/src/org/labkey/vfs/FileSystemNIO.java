package org.labkey.vfs;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;

public class FileSystemNIO extends AbstractFileSystemLike
{
    final java.nio.file.Path nioRoot;
    final _FileLike root;

    FileSystemNIO(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        super(uri, canRead, canWrite, canDeleteRoot);
        nioRoot = java.nio.file.Path.of(uri);
        root = new _FileLike(Path.rootPath, nioRoot);
    }

    @Override
    public FileLike getRoot()
    {
        return root;
    }

    @Override
    public FileLike resolveFile(Path path)
    {
        path = path.absolute().normalize();
        if (null == path)
            throw new IllegalArgumentException("Path could not be resolved");
        var nioPath = nioRoot;
        for (var s : path)
            nioPath = nioPath.resolve(s);
        return new _FileLike(path, nioPath);
    }


    class _FileLike extends AbstractFileLike
    {
        final java.nio.file.Path nioPath;
        final File file;

        _FileLike(Path path, java.nio.file.Path nioPath)
        {
            super(path);
            if (!nioPath.startsWith(nioRoot))
                throw new IllegalArgumentException("Path can not be resolved");
            this.nioPath = nioPath;
            this.file = nioPath.toFile();
        }

        @Override
        public FileSystemLike getFileSystem()
        {
            return FileSystemNIO.this;
        }

        @Override
        public @NotNull Collection<FileLike> getChildren()
        {
            throw new NotImplementedException();
        }

        @Override
        public void _createFile() throws IOException
        {
            FileUtil.createNewFile(file);
        }


        @Override
        final public void _mkdir() throws IOException
        {
            FileUtil.mkdir(file);
        }

        @Override
        final public void _mkdirs() throws IOException
        {
            FileUtil.mkdirs(file);
        }

        @Override
        public boolean exists()
        {
            return file.exists();
        }

        @Override
        public boolean isDirectory()
        {
            return file.isDirectory();
        }

        @Override
        public boolean isFile()
        {
            return file.isFile();
        }

        @Override
        public long getSize()
        {
            return file.length();
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
        }

        @Override
        public int compareTo(@NotNull FileLike o)
        {
            if (!(o instanceof _FileLike fl))
                throw new ClassCastException();
            return nioPath.compareTo(fl.nioPath);
        }

        @Override
        public int hashCode()
        {
            return nioPath.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof _FileLike other))
                return false;
            return nioPath.equals(other.nioPath);
        }
    }
}
