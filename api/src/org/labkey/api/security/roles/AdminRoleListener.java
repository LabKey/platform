package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.Permission;

public interface AdminRoleListener
{
    /**
     * Called when a permission is registered. Gives admin roles the opportunity
     * to include the permission in the set of permissions the role supports.
     */
    void permissionRegistered(Class<? extends Permission> perm);
}
