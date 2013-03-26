/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.management.relation.RoleStatus;
import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;

/**
 * User: adam
 * Date: 4/10/12
 * Time: 8:28 AM
 */
public class ImpersonateRoleContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final Set<String> _roleNames = new HashSet<String>();
    private final URLHelper _returnURL;

    private String _cacheKey;

    public ImpersonateRoleContextFactory(Container project, User adminUser, Role role, URLHelper returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _returnURL = returnURL;
        addRole(role);
    }

    public void addRole(Role role)
    {
        synchronized (_roleNames)
        {
            _roleNames.add(role.getUniqueName());

            // Compute the navtree cache key now; NavTree will be different for each role set + project combination
            StringBuilder cacheKey = new StringBuilder("/impersonationRole=");

            for (String roleName : _roleNames)
            {
                cacheKey.append(roleName);
                cacheKey.append("|");
            }

            if (null != _projectId)
                cacheKey.append("/impersonationProject=" + _projectId);

            _cacheKey = cacheKey.toString();
        }
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        synchronized (_roleNames)
        {
            return new ImpersonateRoleContext(project, getAdminUser(), new HashSet<String>(_roleNames), _returnURL);
        }
    }

    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the role and force permissions check
        getImpersonationContext();
        // TODO: Audit log?
    }

    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }


    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        // TODO: Audit log?
    }

    static void addMenu(NavTree menu, Container c, ActionURL currentURL, Set<Role> roles)
    {
        UserUrls userURLs = PageFlowUtil.urlProvider(UserUrls.class);
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        NavTree roleMenu = new NavTree("Role");

        boolean hasRead = false;

        for (Role role : roles)
        {
            if (role.getPermissions().contains(ReadPermission.class))
            {
                hasRead = true;
                break;
            }
        }

        // Add the relevant roles
        for (Role role : RoleManager.getAllRoles())
        {
            if (role.isAssignable() && role.isApplicable(policy, c))
            {
                NavTree roleItem = new NavTree(role.getName(), userURLs.getImpersonateRoleURL(c, role.getUniqueName(), currentURL));

                // Disable roles that are already being impersonated. Also, disable all roles that don't include read
                // permissions, until a role that does has been selected. #14835
                if (roles.contains(role) || (!hasRead && !role.getPermissions().contains(ReadPermission.class)))
                    roleItem.setDisabled(true);

                roleMenu.addChild(roleItem);
            }
        }

        if (roleMenu.hasChildren())
            menu.addChild(roleMenu);
    }

    public class ImpersonateRoleContext implements ImpersonationContext
    {
        private final @Nullable Container _project;
        /** Hold on to the role names and not the Roles themselves for serialization purposes. See issue 15660 */
        private final Set<String> _roleNames;
        private transient Set<Role> _roles;
        private final URLHelper _returnURL;
        private final User _adminUser;

        private ImpersonateRoleContext(@Nullable Container project, User user, Set<String> roleNames, URLHelper returnURL)
        {
            verifyPermissions(project, user);
            _project = project;
            _roleNames = roleNames;
            _returnURL = returnURL;
            _adminUser = user;
        }

        // Throws if user is not authorized.  Throws IllegalStateException because UI should prevent this... could be a code bug.
        private void verifyPermissions(@Nullable Container project, User user)
        {
            // Site admin can impersonate anywhere
            if (user.isAdministrator())
                return;

            // Must not be root
            if (null == project)
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate a role in this project", getFactory());

            // Must have admin permissions in project
            if (!project.hasPermission(user, AdminPermission.class))
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate a role in this project", getFactory());
        }

        @Override
        public boolean isImpersonating()
        {
            return true;
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            return false;
        }

        @Override
        public @Nullable Container getImpersonationProject()
        {
            return _project;
        }

        @Override
        public User getAdminUser()
        {
            return _adminUser;
        }

        @Override
        public String getNavTreeCacheKey()
        {
            synchronized (_roleNames)
            {
                return _cacheKey;
            }
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

        private synchronized Set<Role> getRoles()
        {
            if (_roles == null)
            {
                _roles = new HashSet<Role>();
                for (String name : _roleNames)
                    _roles.add(RoleManager.getRole(name));
            }
            return _roles;
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return new HashSet<Role>(getRoles());
        }

        @Override
        public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
        {
            ImpersonateRoleContextFactory.addMenu(menu, c, currentURL, getRoles());
        }
    }
}
