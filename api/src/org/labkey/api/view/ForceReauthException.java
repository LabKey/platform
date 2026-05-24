package org.labkey.api.view;

// Use a subclass to prevent permission-checking code paths from setting the wrong type
public class ForceReauthException extends UnauthorizedException
{
    public static final String FORCE_REAUTH_NAME = "_forceReauth";

    @Override
    public Type getType()
    {
        return Type.forceReauth;
    }
}
