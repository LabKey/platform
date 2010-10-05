package org.labkey.api.action;

import org.labkey.api.util.SkipMothershipLogging;

/**
 * Signals the client API caller that they somehow made an invalid request. These errors are not reported to the
 * mothership.
 * User: jeckels
 * Date: Oct 5, 2010
 */
public class ApiUsageException extends RuntimeException implements SkipMothershipLogging
{
    public ApiUsageException()
    {
        super();
    }

    public ApiUsageException(String message)
    {
        super(message);
    }

    public ApiUsageException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ApiUsageException(Throwable cause)
    {
        super(cause);
    }
}
