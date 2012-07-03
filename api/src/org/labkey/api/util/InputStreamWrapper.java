package org.labkey.api.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 7/2/12
 * Time: 5:10 PM
 */
public class InputStreamWrapper extends InputStream
{
    private final InputStream _is;

    public InputStreamWrapper(InputStream is)
    {
        _is = is;
    }

    @Override
    public int read() throws IOException
    {
        return _is.read();
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return _is.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        return _is.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException
    {
        return _is.skip(n);
    }

    @Override
    public int available() throws IOException
    {
        return _is.available();
    }

    @Override
    public void close() throws IOException
    {
        _is.close();
    }

    @Override
    public void mark(int readlimit)
    {
        _is.mark(readlimit);
    }

    @Override
    public void reset() throws IOException
    {
        _is.reset();
    }

    @Override
    public boolean markSupported()
    {
        return _is.markSupported();
    }
}
