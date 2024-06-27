package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AllowedForReadOnlyUser;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.stream.Stream;

public class ReadOnlyImpersonatingContext extends NotImpersonatingContext
{
    @Override
    public Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
    {
        return perms
            .filter(p -> null != p.getAnnotation(AllowedForReadOnlyUser.class));
    }

    @Override
    public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        return super.getAssignedRoles(user, resource).filter(role -> !role.isPrivileged());
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
    }
}
