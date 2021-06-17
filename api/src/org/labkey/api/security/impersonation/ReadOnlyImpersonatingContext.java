package org.labkey.api.security.impersonation;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AllowedForReadOnlyUser;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Set;
import java.util.stream.Collectors;

public class ReadOnlyImpersonatingContext extends NotImpersonatingContext
{
    @Override
    public Set<Class<? extends Permission>> filterPermissions(Set<Class<? extends Permission>> perms)
    {
        var allowed = perms.stream().filter(p -> null != p.getAnnotation(AllowedForReadOnlyUser.class)).collect(Collectors.toSet());
        return allowed;
    }

    @Override
    public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
    {
        var ret = super.getContextualRoles(user, policy);
        ret.remove(RoleManager.siteAdminRole);
        return ret;
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
    }
}
