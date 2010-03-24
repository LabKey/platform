package org.labkey.api.resource;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: Mar 12, 2010 3:03:42 PM
 */
public class FileResource extends AbstractResource
{
    Resolver _resolver;
    File _file;

    FileResource(Resolver resolver, Path path, File file)
    {
        super(path);
        _resolver = resolver;
        _file = file;
    }

    public boolean exists()
    {
        return _file.isFile();
    }

    public Resource parent()
    {
        return _resolver.lookup(getPath().getParent());
    }

    public boolean isFile()
    {
        return exists();
    }

    public long getLastModified()
    {
        return exists() ? _file.lastModified() : Long.MIN_VALUE;
    }

    public InputStream getInputStream() throws IOException
    {
        assert isFile();
        if (isFile())
            return new FileInputStream(_file);
        return null;
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public long getContentLength()
    {
        if (isFile())
            return _file.length();
        return 0;
    }

    // TODO move more functionality into interface and remove this method
    public File getFile()
    {
        return _file;
    }
}
