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
 * A wrapped user that possesses all the permissions associated with that user plus one or more contextual roles
 */
public class ElevatedUser extends ClonedUser
{
    private ElevatedUser(User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        super(user, new WrappedImpersonationContext(user.getImpersonationContext(), getRoles(rolesToAdd)));
    }

    /**
     * Conditionally add roles to the supplied user. For each permission + role pair, add the role if the user doesn't
     * have the corresponding permission in the supplied container.
     */
    @SafeVarargs
    public static User getElevatedUser(Container container, User user, Pair<Class<? extends Permission>, Class<? extends Role>>... pairs)
    {
        Set<Class<? extends Role>> rolesToAdd = Arrays.stream(pairs)
            .filter(pair -> !container.hasPermission(user, pair.first))
            .map(pair -> pair.second)
            .collect(Collectors.toSet());

        return !rolesToAdd.isEmpty() ? getElevatedUser(user, rolesToAdd) : user;
    }

    public static User getElevatedUser(User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        return new ElevatedUser(user, rolesToAdd);
    }

    public static User getCanSeeAuditLogUser(Container container, User user)
    {
        return getElevatedUser(container, user, Pair.of(CanSeeAuditLogPermission.class, CanSeeAuditLogRole.class));
    }
}
