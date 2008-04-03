package org.labkey.api.util;

public class UnexpectedException extends RuntimeException
{
    static public void rethrow(Throwable cause)
    {
        if (cause instanceof RuntimeException)
        {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error)
        {
            throw (Error) cause;
        }
        throw new UnexpectedException(cause);
    }

    static public RuntimeException wrap(Throwable cause)
    {
        if (cause instanceof RuntimeException)
            return (RuntimeException) cause;
        return new UnexpectedException(cause);
    }

    public UnexpectedException(Throwable cause)
    {
        super(cause);
    }

    public String toString()
    {
        return super.toString() + ":" + getCause();
    }
}
