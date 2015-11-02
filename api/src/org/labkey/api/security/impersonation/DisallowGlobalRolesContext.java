package org.labkey.api.security.impersonation;

/**
 * Created by adam on 10/30/2015.
 */

import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;

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

    @Override
    public int[] getGroups(User user)
    {
        int[] groups = super.getGroups(user);
        return ArrayUtils.removeElements(groups, Group.groupAdministrators, Group.groupDevelopers);
    }
}
