package org.labkey.api.security;

import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.Permission;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A cloned user that has the user's permissions in all the user's containers, but always restricted to the supplied
 * permissions. This is useful for creating read-only API keys, editor-only API keys, etc.
 */
public class PermissionsRestrictedUser extends ClonedUser
{
    public static final String ALLOWED_PERMISSIONS_KEY = PermissionsRestrictedUser.class.getName() + "$AllowedPermissionsKey";

    private static class RoleRestrictedPermissionsContext extends WrappedPermissionsContext
    {
        private final Set<Class<? extends Permission>> _allowedPermissions;

        private RoleRestrictedPermissionsContext(PermissionsContext ctx, Set<Class<? extends Permission>> allowedPermissions)
        {
            super(ctx);
            _allowedPermissions = allowedPermissions;
        }

        @Override
        public Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
        {
            return super.filterPermissions(perms).filter(_allowedPermissions::contains);
        }

        @Override
        public void modifySession(HttpSession session)
        {
            super.modifySession(session);
            session.setAttribute(ALLOWED_PERMISSIONS_KEY, _allowedPermissions);
        }
    }

    public PermissionsRestrictedUser(User user, @NotNull Set<Class<? extends Permission>> allowedPermissions)
    {
        super(user, new RoleRestrictedPermissionsContext(user.getPermissionsContext(), allowedPermissions));
    }
}
