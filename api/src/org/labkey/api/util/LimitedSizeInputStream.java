package org.labkey.api.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Limits the underlying InputStream to return at most a given number of bytes.
 * Taken from https://stackoverflow.com/questions/15445504/copy-inputstream-abort-operation-if-size-exceeds-limit
 */
public class LimitedSizeInputStream extends InputStream
{
    private final InputStream original;
    private final long maxSize;
    private long total;

    public LimitedSizeInputStream(InputStream original, long maxSize)
    {
        this.original = original;
        this.maxSize = maxSize;
    }

    @Override
    public int read() throws IOException
    {
        int i = original.read();
        if (i >= 0) incrementCounter(1);
        return i;
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        int i = original.read(b, off, len);
        if (i >= 0) incrementCounter(i);
        return i;
    }

    private void incrementCounter(int size) throws IOException
    {
        if (total + size > maxSize) throw new LimitReachedException("InputStream exceeded maximum size of " + maxSize + " bytes.", total);
        total += size;
    }

    /**
     * Thrown to indicate we hit the cap
     */
    public static class LimitReachedException extends IOException
    {
        private final long _bytesRead;

        public LimitReachedException(String message, long bytesRead)
        {
            super(message);
            _bytesRead = bytesRead;
        }

        public long getBytesRead()
        {
            return _bytesRead;
        }
    }

    @Override
    public void close() throws IOException
    {
        if (original != null)
            original.close();

        super.close();
    }
}
