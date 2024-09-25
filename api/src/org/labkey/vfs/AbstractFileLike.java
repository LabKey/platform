package org.labkey.vfs;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

abstract public class AbstractFileLike implements FileLike
{

    final Path path;

    protected AbstractFileLike(Path path)
    {
        // there should be no way to get here with a non-normalized path
        if (path.normalize() != path)
            throw new IllegalArgumentException("Path must be normalized");
        if (!path.isAbsolute())
            throw new IllegalArgumentException("Path must be absolute");
        this.path = path;
    }

    @Override
    final public Path getPath()
    {

        return path;
    }

    @Override
    final public FileLike getParent()
    {
        if (path.isEmpty())
            return null;
        return getFileSystem().resolveFile(path.getParent());
    }

    @Override
    public void createFile() throws IOException
    {
        var parent = getParent();
        if (null == parent || !parent.isDirectory())
            throw new IOException("Parent is not a directory");
        _createFile();
    }

    abstract protected void _createFile() throws IOException;

    @Override
    public FileSystemLike getFileSystem()
    {
        return null;
    }

    @Override
    public @NotNull Collection<FileLike> getChildren()
    {
        return List.of();
    }

    @Override
    public void refresh()
    {
    }

    @Override
    public boolean exists()
    {
        return false;
    }

    @Override
    public boolean isDirectory()
    {
        return false;
    }

    @Override
    public boolean isFile()
    {
        return false;
    }

    @Override
    public long getSize()
    {
        return 0;
    }

    @Override
    public OutputStream openOutputStream() throws IOException
    {
        return null;
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        return null;
    }

    @Override
    public int compareTo(@NotNull FileLike o)
    {
        return 0;
    }

    @Override
    public void mkdir() throws IOException
    {
        refresh();
        if (exists())
            return;
        if (path.isEmpty() || !getFileSystem().getRoot().isDirectory())
            throw new IOException("File root does not exist");
        var parent = getParent();
        if (null == parent || !getParent().isDirectory())
            throw new IOException("Parent is not a folder");
        _mkdir();
    }

    abstract protected void _mkdir() throws IOException;

    @Override
    public void mkdirs() throws IOException
    {
        refresh();
        if (exists())
            return;
        if (path.isEmpty() || !getFileSystem().getRoot().isDirectory())
            throw new IOException("File root does not exist");
        _mkdirs();
    }

    abstract protected void _mkdirs() throws IOException;

    // package helpers
    static Path resolvePath(Path base, Path path)
    {
        assert base.isAbsolute();
        if (path.isEmpty())
            return base;
        if (path.isAbsolute() || base.isEmpty())
            return path;
        var resolved = base.resolve(path);
        resolved = resolved.normalize();
        if (null == resolved)
            throw new IllegalArgumentException("Illegal path " + path + " in " + base);
        assert resolved.isAbsolute();
        return resolved;
    }

    @Override
    final public FileLike resolveFile(Path file)
    {
        var absolutePath = resolvePath(this.path, file);
        assert absolutePath.isAbsolute();
        return getFileSystem().resolveFile(absolutePath);
    }
}
