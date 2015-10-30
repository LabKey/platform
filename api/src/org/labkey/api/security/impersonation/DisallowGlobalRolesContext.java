package org.labkey.api.security.impersonation;

/**
 * Created by adam on 10/30/2015.
 */

/**
 * A "not impersonating" context that disallows all global roles like Site Admin and Developer
 */
public class DisallowGlobalRolesContext extends NotImpersonatingContext
{
    @Override
    public boolean isAllowedGlobalRoles()
    {
        return false;
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return "DisallowGlobalRoles";
    }
}
