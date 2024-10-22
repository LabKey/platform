package org.labkey.vfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

public class FileSystemLocal extends AbstractFileSystemLike
{
    final java.nio.file.Path nioRoot;
    final _FileLike root;
    final boolean caching;

    FileSystemLocal(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        this(uri, false, canRead, canWrite, canDeleteRoot);
    }

    private FileSystemLocal(URI uri, boolean caching, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        super(uri, canRead, canWrite, canDeleteRoot);
        this.nioRoot = java.nio.file.Path.of(uri);
        this.root = createFileLike(Path.rootPath, new File(uri));
        this.caching = caching;
    }

    @Override
    public FileSystemLike getCachingFileSystem()
    {
        if (caching)
            return this;
        return new FileSystemLocal(getURI(), true, canReadFiles(), canWriteFiles(), canDeleteRoot());
    }

    @Override
    public java.nio.file.Path getNioPath(FileLike fo)
    {
        return ((_FileLike)fo).file.toPath();
    }

    @Override
    public URI getURI(FileLike fo)
    {
        return ((_FileLike)fo).file.toPath().toUri();
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
        return createFileLike(path, nioPath.toFile());
    }


    _FileLike createFileLike(Path path, File file)
    {
        if (caching)
            return new _CachingFileLike(path, file);
        else
            return new _FileLike(path, file);
    }


    @JsonSerialize(using = FileLike.FileLikeSerializer.class)
    @JsonDeserialize(using = FileLike.FileLikeDeserializer.class)
    class _FileLike extends AbstractFileLike
    {
        final File file;

        _FileLike(Path path, File file)
        {
            super(path);
            var nioPath = file.toPath();
            if (!nioPath.startsWith(nioRoot))
                throw new IllegalArgumentException("Path can not be resolved");
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
            return Arrays.stream(children).map(f -> (FileLike)createFileLike(path.append(f.getName()), f)).toList();
        }

        @Override
        public void _createFile() throws IOException
        {
            if (!canWriteFiles())
                throw new UnauthorizedException();
            try
            {
                FileUtil.createNewFile(file);
            }
            finally
            {
                refresh();
            }
        }

        @Override
        public boolean delete() throws IOException
        {
            if (!canWriteFiles())
                throw new UnauthorizedException();
            try
            {
                return file.delete();
            }
            finally
            {
                refresh();
            }
        }

        @Override
        final public void _mkdirs() throws IOException
        {
            if (!canWriteFiles())
                throw new UnauthorizedException();
            try
            {
                FileUtil.mkdirs(file);
            }
            finally
            {
                refresh();
            }
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
        public long getLastModified()
        {
            return file.lastModified();
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
            // assert to find bad usages
            assert !(obj instanceof File);
            if (!(obj instanceof _FileLike other))
                return false;
            return file.equals(other.file);
        }

        @Override
        public String toString()
        {
            return file.toPath().toUri().toString();
        }
    }


    class _CachingFileLike extends _FileLike
    {
        String[] childrenNames;
        MinimalFileAttributes attributes;

        _CachingFileLike(Path path, File file)
        {
            super(path, file);
            attributes = null;
            childrenNames = null;
        }

        @NotNull MinimalFileAttributes getAttributes()
        {
            synchronized (this)
            {
                if (null != attributes)
                    return attributes;
            }

            try
            {
                var att = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                synchronized (this)
                {
                    attributes = new MinimalFileAttributes(true, att.isRegularFile(), !att.isRegularFile(), att.size(), att.lastModifiedTime().toMillis());
                    return attributes;
                }
            }
            catch (FileNotFoundException | InvalidPathException | NoSuchFileException x)
            {
                synchronized (this)
                {
                    attributes = NULL_ATTRIBUTES;
                    return attributes;
                }
            }
            catch (IOException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }

        @Override
        public void refresh()
        {
            synchronized (this)
            {
                attributes = null;
                childrenNames = null;
            }
        }

        @Override
        public boolean exists()
        {
            return getAttributes().exists();
        }

        @Override
        public boolean isDirectory()
        {
            return getAttributes().directory();
        }

        @Override
        public boolean isFile()
        {
            return getAttributes().file();
        }

        @Override
        public long getSize()
        {
            return getAttributes().size();
        }

        @Override
        public @NotNull List<FileLike> getChildren()
        {
            if (!isDirectory())
                return List.of();

            String[] children = null;

            synchronized (this)
            {
                if (null != childrenNames)
                    children = childrenNames;
            }
            if (null != children)
                return Arrays.stream(children).map(this::resolveChild).toList();

            var list = super.getChildren();
            synchronized (this)
            {
                childrenNames = list.stream().map(FileLike::getName).toList().toArray(new String[0]);
            }
            return list;
        }
    }
}
