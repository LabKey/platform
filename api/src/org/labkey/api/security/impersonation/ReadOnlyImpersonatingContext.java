package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AllowedForReadOnlyUser;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Set;
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
    public Set<Role> getAssignedRoles(User user, SecurityPolicy policy)
    {
        var ret = super.getAssignedRoles(user, policy);
        ret.removeIf(Role::isPrivileged);
        return ret;
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
    }
}
