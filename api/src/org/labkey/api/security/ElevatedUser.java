package org.labkey.api.security;

import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.data.Container;
import org.labkey.api.security.impersonation.WrappedImpersonationContext;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A wrapped user that possesses all the security properties (groups, roles, impersonation status, etc.) of the
 * underlying user and adds one or more contextual roles. Use this class as a last resort; preference is to use
 * contextual roles in individual permission checks or LimitedUser.
 */
public class ElevatedUser extends ClonedUser
{
    private ElevatedUser(User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        super(user, new WrappedImpersonationContext(user.getImpersonationContext(), getRoles(rolesToAdd)));
    }

    /**
     * Wrap the supplied user and unconditionally add the supplied role(s). Always returns an ElevatedUser.
     */
    public static ElevatedUser getElevatedUser(User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        return new ElevatedUser(user, rolesToAdd);
    }

    /**
     * Ensure the supplied user has the supplied permissions. If so, return that user. If not, wrap the user with
     * ElevatedUser and, for each pair of permission + role, add the role if the user doesn't have the corresponding
     * permission in the supplied container.
     */
    @SafeVarargs
    public static User ensureContextualRoles(Container container, User user, Pair<Class<? extends Permission>, Class<? extends Role>>... pairs)
    {
        Set<Class<? extends Role>> rolesToAdd = Arrays.stream(pairs)
            .filter(pair -> !container.hasPermission(user, pair.first))
            .map(pair -> pair.second)
            .collect(Collectors.toSet());

        return !rolesToAdd.isEmpty() ? getElevatedUser(user, rolesToAdd) : user;
    }

    /**
     * Ensure the supplied user can read the audit log
     */
    public static User ensureCanSeeAuditLogRole(Container container, User user)
    {
        return ensureContextualRoles(container, user, Pair.of(CanSeeAuditLogPermission.class, CanSeeAuditLogRole.class));
    }
}
