package org.labkey.api.security.impersonation;

/**
 * Created by adam on 10/30/2015.
 */

/**
 * A "not impersonating" context that disallows all global roles (i.e., Site Admin and Developer)
 */
public class DisallowGlobalRolesContext extends NotImpersonatingContext
{
    private static final DisallowGlobalRolesContext INSTANCE = new DisallowGlobalRolesContext();

    public static DisallowGlobalRolesContext get()
    {
        return INSTANCE;
    }

    private DisallowGlobalRolesContext()
    {
    }

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
