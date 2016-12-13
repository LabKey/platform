package org.labkey.api.security;

/**
 * Created by xingyang on 12/12/16.
 */
public class LoginDisabledException extends Exception
{
    public LoginDisabledException(String message)
    {
        super(message);
    }

    public LoginDisabledException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

