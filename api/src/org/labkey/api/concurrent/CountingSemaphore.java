package org.labkey.api.concurrent;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by matthew on 3/13/14.
 *
 * Wrapper for java.util.concurrent.Semaphore that returns a Closeable
 */
public class CountingSemaphore
{
    final Semaphore s;

    public CountingSemaphore(int permits, boolean fair)
    {
        s = new Semaphore(permits, fair);
    }


    public AutoCloseable tryAcquire(long timeout) throws InterruptedException
    {
        boolean b = s.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        return b ? new _Permit() : null;
    }


    public AutoCloseable acquire() throws InterruptedException
    {
        s.acquire();
        return new _Permit();
    }

    private class _Permit implements AutoCloseable
    {
        @Override
        public void close() throws Exception
        {
            s.release();
        }
    }
}
