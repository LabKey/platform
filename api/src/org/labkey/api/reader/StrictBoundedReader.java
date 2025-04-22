package org.labkey.api.reader;

import java.io.IOException;
import java.io.Reader;

/**
 * Restricts the Reader to a character limit. Throws an exception when the limit is exceeded.
 * Similar to Apache's BoundedReader, which silently truncates.
 */
public class StrictBoundedReader extends Reader
{
    private final Reader in;
    private final long maxChars;
    private long charsRead = 0;
    private boolean closed = false;

    public static class LimitExceededException extends IOException
    {
        public LimitExceededException(String message)
        {
            super(message);
            
        }
    }

    public StrictBoundedReader(Reader in, long maxChars)
    {
        if (in == null)
        {
            throw new NullPointerException("Reader cannot be null");
        }
        if (maxChars < 0)
        {
            throw new IllegalArgumentException("maxChars must be non-negative");
        }
        this.in = in;
        this.maxChars = maxChars;
    }

    @Override
    public int read() throws IOException
    {
        ensureOpen();
        if (charsRead >= maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }
        int result = in.read();
        if (result != -1)
        {
            charsRead++;
        }
        return result;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException
    {
        ensureOpen();
        if (charsRead >= maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }

        long remaining = maxChars - charsRead;
        int toRead = (int) Math.min(len, remaining);
        int numRead = in.read(cbuf, off, toRead);

        if (numRead > 0)
        {
            charsRead += numRead;
        }

        if (charsRead > maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }

        return numRead;
    }

    private void ensureOpen() throws IOException
    {
        if (closed)
        {
            throw new IOException("Reader is closed");
        }
    }

    @Override
    public void close() throws IOException
    {
        if (!closed)
        {
            in.close();
            closed = true;
        }
    }
}
