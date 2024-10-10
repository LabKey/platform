package org.labkey.vfs;

import java.net.URI;
import java.nio.file.Path;

abstract public class AbstractFileSystemLike implements FileSystemLike
{
    final URI uri;
    final String scheme;
    final String strUri;    // no trailing '/'
    final boolean canDeleteRoot;
    final boolean canList = true;
    final boolean canRead;
    final boolean canWrite;

    AbstractFileSystemLike(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        // Is there value in re-encoding the URI so that it is consistently encoded?
        this.uri = uri;
        this.strUri = uri.toString();
        this.scheme = uri.getScheme();
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canDeleteRoot = canDeleteRoot;
    }

    /* wrapper to make sure we're being consistent */
    String toURIPath(org.labkey.api.util.Path path)
    {
        return path.encode();
    }

    @Override
    public URI getURI()
    {
        return uri;
    }

    @Override
    public String getScheme()
    {
        return scheme;
    }

    @Override
    public Path getNioPath(FileLike fo)
    {
        return Path.of(getURI(fo));
    }

    @Override
    public boolean canDeleteRoot()
    {
        return canDeleteRoot;
    }

    @Override
    public boolean canList()
    {
        return canList;
    }

    @Override
    public boolean canReadFiles()
    {
        return canRead;
    }

    @Override
    public boolean canWriteFiles()
    {
        return canWrite;
    }
}
