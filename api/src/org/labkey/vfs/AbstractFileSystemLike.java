package org.labkey.vfs;

import java.net.URI;
import java.nio.file.Path;

import static org.labkey.api.files.virtual.AuthorizedFileSystem.createReadOnly;
import static org.labkey.api.files.virtual.AuthorizedFileSystem.toURIPath;

abstract public class AbstractFileSystemLike implements FileSystemLike
{
    final URI uri;
    final String strUri;    // no trailing '/'
    final boolean canDeleteRoot;
    final boolean canList = true;
    final boolean canRead;
    final boolean canWrite;


    AbstractFileSystemLike(URI uri, boolean canRead, boolean canWrite, boolean canDeleteRoot)
    {
        var scheme = uri.getScheme();
        var path = uri.getPath();
        this.strUri = scheme + "://" + path;
        this.uri = URI.create(strUri);
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
    public URI getURI(FileLike fo)
    {
        return URI.create(strUri + toURIPath(fo.getPath()));
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
