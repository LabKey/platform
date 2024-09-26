package org.labkey.vfs;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;

public interface FileLike extends Comparable<FileLike>
{
    FileSystemLike getFileSystem();

    /*
     * This is the path within the containing FileSystemLike object.  Will always be absolute, meaning
     * getPath().toString() will always start with '/'.  Depending on how the path was created, it may
     * end with '/'. (As always be careful when resolving paths that start with ".".)
     */
    Path getPath();

    default String getName()
    {
        return getPath().getName();
    }

    default URI toURI()
    {
        return getFileSystem().getURI(this);
    }

    default java.nio.file.Path toNioPathForRead()
    {
        if (!getFileSystem().canReadFiles())
            throw new UnauthorizedException();
        return getFileSystem().getNioPath(this);
    }

    default java.nio.file.Path toNioPathForWrite()
    {
        if (!getFileSystem().canWriteFiles())
            throw new UnauthorizedException();
        return getFileSystem().getNioPath(this);
    }

    /* We use util.Path here to avoid ambiguity of String (encoded vs not encoded, path vs name, etc). */
    FileLike resolveFile(org.labkey.api.util.Path path);

    default FileLike resolveChild(String name)
    {
        if (".".equals(name) || "..".equals(name))
            throw new IllegalArgumentException("Cannot resolve child '" + name + "'");
        Path path = Path.parse(name);
        if (1 != path.size())
            throw new IllegalArgumentException("Cannot resolve child '" + name + "'");
        return resolveFile(path);
    }

    FileLike getParent();

    @NotNull
    List<FileLike> getChildren();

    /** Does not create parent directories */
    void mkdir() throws IOException;

    void mkdirs() throws IOException;

    /** Does not create parent directories */
    void createFile() throws IOException;

    void delete() throws IOException;

    void refresh();

    boolean exists();

    boolean isDirectory();

    boolean isFile();

    long getSize();

    OutputStream openOutputStream() throws IOException;

    InputStream openInputStream() throws IOException;
}
