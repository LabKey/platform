package org.labkey.api.security;

import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A cloned user that has the user's permissions in all the user's containers, but always restricted to the supplied
 * role. This is useful for creating read-only API keys, editor-only API keys, etc.
 */
public class RoleRestrictedUser extends ClonedUser
{
    private static class RoleRestrictedPermissionsContext extends WrappedPermissionsContext
    {
        private final Role _role;

        private RoleRestrictedPermissionsContext(PermissionsContext ctx, Class<? extends Role> restrictionRole)
        {
            super(ctx);
            _role = RoleManager.getRole(restrictionRole);
        }

        @Override
        public Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
        {
            Set<Class<? extends Permission>> rolePermissions = _role.getPermissions();
            return super.filterPermissions(perms).filter(rolePermissions::contains);
        }
    }

    public RoleRestrictedUser(User user, Class<? extends Role> restrictionRole)
    {
        super(user, new RoleRestrictedPermissionsContext(user.getPermissionsContext(), restrictionRole));
    }
}
