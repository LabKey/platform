package org.labkey.wiki.export;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.AbstractWebdavResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: jeckels
 * Date: Jan 19, 2012
 */
public class DummyWebdavResource extends AbstractWebdavResource
{
    public DummyWebdavResource()
    {
        super(new Path("fakePath"));
    }

    @Override
    public InputStream getInputStream(User user) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getContentLength() throws IOException
    {
        throw new UnsupportedOperationException();
    }
}
