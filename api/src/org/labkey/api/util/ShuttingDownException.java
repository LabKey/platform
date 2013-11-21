package org.labkey.api.util;

/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 11/7/13
 * Time: 9:18 AM
 */
public class ShuttingDownException extends RuntimeException
{
    public ShuttingDownException()
    {
        super("Server is shutting down");
    }

    public ShuttingDownException(String msg)
    {
        super(msg);
    }
}
