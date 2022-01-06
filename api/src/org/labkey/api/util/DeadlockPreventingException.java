package org.labkey.api.util;

/**
 * Thrown after a resource doesn't become available after a timeout in an attempt to avoid deadlocking the whole server.
 */
public class DeadlockPreventingException extends RuntimeException
{
    public DeadlockPreventingException(String message)
    {
        super(message);
    }

    public DeadlockPreventingException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
