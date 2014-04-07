package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;

/**
 * User: kevink
 * Date: 3/30/14
 *
 * Simple success/fail response message with optional message.
 */
public class SimpleResponse
{
    private boolean _success;
    private String _message;

    public SimpleResponse(boolean success)
    {
        this(success, null);
    }

    public SimpleResponse(boolean success, @Nullable String message)
    {
        _success = success;
        _message = message;
    }

    public boolean isSuccess()
    {
        return _success;
    }

    public String getMessage()
    {
        return _message;
    }
}
