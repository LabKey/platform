package org.labkey.api.util;

/**
 * Thrown after a resource doesn't become available after a timeout in an attempt to avoid deadlocking the whole server.
 * Dump threads at construction time since throwing this exception will likely release locks and destroy all evidence of
 * the deadlock.
 */
public class DeadlockPreventingException extends RuntimeException
{
    public DeadlockPreventingException(String message)
    {
        super(message);
        dumpThreads();
    }

    public DeadlockPreventingException(String message, Throwable cause)
    {
        super(message, cause);
        dumpThreads();
    }

    private void dumpThreads()
    {
        DebugInfoDumper.dumpThreads(1);
    }
}
