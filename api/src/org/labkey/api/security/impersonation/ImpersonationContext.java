package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.io.Serializable;

/**
 * User: adam
 * Date: 11/8/11
 * Time: 8:01 PM
 */
public interface ImpersonationContext extends Serializable
{
    public boolean isImpersonated();
    public boolean isAllowedRoles();
    public Container getStartingProject();
    public Container getImpersonationProject();
    public User getImpersonatingUser();
    public String getNavTreeCacheKey();  // Caching permission-related state is very tricky with impersonation; context needs to provide the cache key suffix
    public URLHelper getReturnURL();
}
