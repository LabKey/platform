package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: 7/2/12
 * Time: 5:16 PM
 */

// Verifies that close() was called at some point before finalization; logs an error and creation stack trace if not.
public class CheckedInputStream extends InputStreamWrapper
{
    private final StackTraceElement[] _creationStackTrace;
    private boolean _closed = false;

    public CheckedInputStream(InputStream is)
    {
        super(is);
        _creationStackTrace = Thread.currentThread().getStackTrace();
    }

    @Override
    public void close() throws IOException
    {
        _closed = true;
        super.close();
    }

    @Override
    protected void finalize() throws Throwable
    {
        if (!_closed)
        {
            Logger.getLogger(CheckedInputStream.class).error("InputStream was not closed. Creation stacktrace:" + ExceptionUtil.renderStackTrace(_creationStackTrace));
            super.close();
        }

        super.finalize();
    }
}
