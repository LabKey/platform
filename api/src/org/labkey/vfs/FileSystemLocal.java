package org.labkey.vfs;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FileSystemLocal extends AbstractFileSystemLike
{
    final java.nio.file.Path nioRoot;
    final _FileLike root;

    FileSystemLocal(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
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
//        final java.nio.file.Path nioPath;
        final File file;

        _FileLike(Path path, java.nio.file.Path nioPath)
        {
            super(path);
            if (!nioPath.startsWith(nioRoot))
                throw new IllegalArgumentException("Path can not be resolved");
//            this.nioPath = nioPath;
            this.file = nioPath.toFile();
        }

        _FileLike(Path path, File file)
        {
            super(path);
            var nioPath = file.toPath();
            if (!nioPath.startsWith(nioRoot))
                throw new IllegalArgumentException("Path can not be resolved");
//            this.nioPath = nioPath;
            this.file = file;
        }

        @Override
        public FileSystemLike getFileSystem()
        {
            return FileSystemLocal.this;
        }

        @Override
        public @NotNull List<FileLike> getChildren()
        {
            if (!canList())
                throw new UnauthorizedException();
            File[] children = file.listFiles();
            if (null == children || 0 == children.length)
                return List.of();
            return Arrays.stream(children).map(f -> (FileLike)new _FileLike(path.append(f.getName()), f)).toList();
        }

        @Override
        public void _createFile() throws IOException
        {
            FileUtil.createNewFile(file);
        }

        @Override
        public void delete() throws IOException
        {
            if (!canWriteFiles())
                throw new UnauthorizedException();
            file.delete();
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
            if (!canWriteFiles())
                throw new UnauthorizedException();
            return new FileOutputStream(file);
        }

        @Override
        public InputStream openInputStream() throws IOException
        {
            if (!canReadFiles())
                throw new UnauthorizedException();
            return new FileInputStream(file);
        }

        @Override
        public int compareTo(@NotNull FileLike o)
        {
            if (!(o instanceof _FileLike fl))
                throw new ClassCastException();
            return file.compareTo(fl.file);
        }

        @Override
        public int hashCode()
        {
            return file.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof _FileLike other))
                return false;
            return file.equals(other.file);
        }
    }
}
