package org.labkey.api.security.impersonation;

import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;

/**
 * User: adam
 * Date: 5/6/12
 * Time: 5:10 PM
 */
public class UnauthorizedImpersonationException extends UnauthorizedException
{
    private final ImpersonationContextFactory _factory;

    UnauthorizedImpersonationException(String message, ImpersonationContextFactory factory)
    {
        super(message);
        _factory = factory;
    }

    public ImpersonationContextFactory getFactory()
    {
        return _factory;
    }
}
