package org.labkey.vfs;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public interface FileLike extends Comparable<FileLike>
{
    FileSystemLike getFileSystem();

    /*
     * This is the path within the containing FileSystemLike object.  Will always be absolute, meaning
     * getPath().toString() will always start with '/'.  Depending on how the path was created, it may
     * end with '/'. (As always be careful when resolving paths that start with ".".)
     */
    Path getPath();

    /* We use util.Path here to avoid ambiguity of String (encoded vs not encoded, path vs name, etc). */
    FileLike resolveFile(org.labkey.api.util.Path path);

    FileLike getParent();

    @NotNull
    Collection<FileLike> getChildren();

    /** Does not create parent directories */
    void mkdir() throws IOException;

    void mkdirs() throws IOException;

    /** Does not create parent directories */
    void createFile() throws IOException;

    void refresh();

    boolean exists();

    boolean isDirectory();

    boolean isFile();

    long getSize();

    OutputStream openOutputStream() throws IOException;

    InputStream openInputStream() throws IOException;
}
