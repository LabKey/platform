package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.SkipMothershipLogging;

/**
 * Exception that should convey a specific HTTP status code in the response to the client, and doesn't indicate
 * a bug or other type of server-side problem
 */
public class HttpStatusException extends RuntimeException implements SkipMothershipLogging
{
    final int status;

    public HttpStatusException(String message, @Nullable Throwable x, int status)
    {
        super(message, x);
        this.status = status;
    }


    /** @return the HTTP status code to be used for the response */
    public int getStatus()
    {
        return status;
    }

}
