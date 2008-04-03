package org.labkey.api.data;

import java.io.InputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;

/**
 * User: brittp
 * Date: Aug 20, 2006
 * Time: 10:50:39 AM
 */
public class ResourceInputStream extends InputStream
{
    public static interface ResourceCloser
    {
        public void close();
    }

    private ResourceCloser _closer;
    private InputStream _wrapped;

    public ResourceInputStream(InputStream stream, ResourceCloser closer)
    {
        _wrapped = stream;
        _closer = closer;
    }

    public int read() throws IOException
    {
        return _wrapped.read();
    }

    public int available() throws IOException
    {
        return _wrapped.available();
    }

    public void close() throws IOException
    {
        _closer.close();
        _wrapped.close();
    }

    public synchronized void mark(int readlimit)
    {
        _wrapped.mark(readlimit);
    }

    public boolean markSupported()
    {
        return _wrapped.markSupported();
    }

    public int read(byte b[]) throws IOException
    {
        return _wrapped.read(b);
    }

    public int read(byte b[], int off, int len) throws IOException
    {
        return _wrapped.read(b, off, len);
    }

    public synchronized void reset() throws IOException
    {
        _wrapped.reset();
    }

    public long skip(long n) throws IOException
    {
        return _wrapped.skip(n);
    }
}
