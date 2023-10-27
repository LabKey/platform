package org.labkey.api.security;

import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    protected static Set<Role> getRoles(Collection<Class<? extends Role>> rolesToAdd)
    {
        return rolesToAdd.stream()
            .map(RoleManager::getRole)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
