package org.labkey.api.security;

import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ClonedUser extends User
{
    protected ClonedUser(User user, ImpersonationContext ctx)
    {
        super(user.getEmail(), user.getUserId());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setActive(user.isActive());
        setDisplayName(user.getFriendlyName());
        setLastLogin(user.getLastLogin());
        setPhone(user.getPhone());
        setLastLogin(user.getLastLogin());
        setLastActivity(user.getLastActivity());

        setImpersonationContext(ctx);
    }

    // Map a stream of role classes to a set of roles
    protected static Set<Role> getRoles(Stream<Class<? extends Role>> roleClassStream)
    {
        return roleClassStream
            .filter(Objects::nonNull)
            .map(RoleManager::getRole)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    // Map a collection of role classes to a set of roles
    protected static Set<Role> getRoles(Collection<Class<? extends Role>> roleClassCollection)
    {
        return getRoles(roleClassCollection.stream());
    }

    // Map an array of role classes to a set of roles
    protected static Set<Role> getRoles(Class<? extends Role>[] roleClassArray)
    {
        return getRoles(Arrays.stream(roleClassArray));
    }
}
