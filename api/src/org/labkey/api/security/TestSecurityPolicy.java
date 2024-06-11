package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.Permission;

// Normal permission checking operates on secure resources, but some unit tests need to update and interrogate
// security policies directly. This class allows those tests to permission check directly on the policy.
public class TestSecurityPolicy extends MutableSecurityPolicy
{
    public TestSecurityPolicy(@NotNull SecurableResource resource)
    {
        super(resource);
    }

    public boolean hasPermission(@NotNull UserPrincipal principal, @NotNull Class<? extends Permission> permission)
    {
        return getOwnPermissions(principal).contains(permission);
    }
}
