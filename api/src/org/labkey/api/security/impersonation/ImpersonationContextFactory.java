package org.labkey.api.security.impersonation;

import java.io.Serializable;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 9:58 AM
 */
// We store implementations of this interface in session and construct ImpersonationContexts at each request.  This
// protects us somewhat from user, container, etc. objects getting out-of-date.
public interface ImpersonationContextFactory extends Serializable
{
    public ImpersonationContext getImpersonationContext();
}
