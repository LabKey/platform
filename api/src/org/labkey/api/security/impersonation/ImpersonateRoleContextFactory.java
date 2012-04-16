package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: adam
 * Date: 4/10/12
 * Time: 8:28 AM
 */
public class ImpersonateRoleContextFactory implements ImpersonationContextFactory
{
    private final GUID _projectId;
    private final int _impersonatingUserId;
    private final String _roleName;
    private final URLHelper _returnURL;

    public ImpersonateRoleContextFactory(Container project, User impersonatingUser, Role role, URLHelper returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _impersonatingUserId = impersonatingUser.getUserId();
        _roleName = role.getUniqueName();
        _returnURL = returnURL;
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);
        Role role = RoleManager.getRole(_roleName);
        User impersonatingUser = UserManager.getUser(_impersonatingUserId);

        return new ImpersonateRoleContext(project, impersonatingUser, role, _returnURL);
    }

    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the role and force permissions check
        getImpersonationContext();
        // TODO: Audit log?
    }

    @Override
    public void stopImpersonating(ViewContext context)
    {
        // TODO: Audit log?
    }

    public class ImpersonateRoleContext implements ImpersonationContext
    {
        private final @Nullable Container _project;
        private final Role _role;
        private final URLHelper _returnURL;

        private ImpersonateRoleContext(@Nullable Container project, User user, Role role, URLHelper returnURL)
        {
            // TODO: Check permissions
            _project = project;
            _role = role;
            _returnURL = returnURL;
        }

        @Override
        public boolean isImpersonated()
        {
            return true;
        }

        @Override
        public boolean isAllowedRoles()
        {
            return false;
        }

        @Override
        public Container getStartingProject()
        {
            return _project;
        }

        @Override
        public Container getImpersonationProject()
        {
            return null;
        }

        @Override
        public User getImpersonatingUser()
        {
            return null;
        }

        @Override
        public String getNavTreeCacheKey()
        {
            // NavTree for user impersonating a role will be different for each role (TODO:??)
            return "/impersonationRole=" + _role.getUniqueName();
        }

        @Override
        public URLHelper getReturnURL()
        {
            return _returnURL;
        }

        @Override
        public int[] getGroups(User user)
        {
            return new int[0];
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return ImpersonateRoleContextFactory.this;
        }

        @Override
        public Set<Role> getContextualRoles(User user)
        {
            Set<Role> roles = new HashSet<Role>();
            roles.add(_role);
            return roles;
        }
    }
}
