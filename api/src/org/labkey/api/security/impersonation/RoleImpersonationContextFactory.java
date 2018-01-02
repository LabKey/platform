/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: adam
 * Date: 4/10/12
 * Time: 8:28 AM
 */
public class RoleImpersonationContextFactory extends AbstractImpersonationContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final Set<String> _roleNames;
    private final Set<String> _previousRoleNames;
    private final ActionURL _returnURL;
    private final String _cacheKey;

    public RoleImpersonationContextFactory(@Nullable Container project, User adminUser, Collection<Role> newImpersonationRoles, Set<Role> currentImpersonationRoles, ActionURL returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _returnURL = returnURL;

        // Compute the navtree cache key based on role names + project: NavTree will be different for each role set + project combination
        StringBuilder cacheKey = new StringBuilder("/impersonationRole=");
        Set<String> roleNames = new HashSet<>();

        for (Role role : newImpersonationRoles)
        {
            String roleName = role.getUniqueName();
            roleNames.add(roleName);

            cacheKey.append(roleName);
            cacheKey.append("|");
        }

        Set<String> oldRoleNames = new HashSet<>();
        currentImpersonationRoles.forEach(oldRole -> oldRoleNames.add(oldRole.getUniqueName()));
        _previousRoleNames = oldRoleNames;

        _roleNames = Collections.unmodifiableSet(roleNames);

        if (null != _projectId)
            cacheKey.append("/impersonationProject=").append(_projectId);

        _cacheKey = cacheKey.toString();
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        return new RoleImpersonationContext(project, getAdminUser(), _roleNames, _returnURL);
    }

    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the role and force permissions check
        getImpersonationContext();

        // Stash (and remove) just the registered session attributes (e.g., permissions-related attributes)
        stashRegisteredSessionAttributes(context.getSession());

        User adminUser = getAdminUser();

        if (!_previousRoleNames.isEmpty())
        {
            UserManager.UserAuditEvent stopEvent = new UserManager.UserAuditEvent(context.getContainer().getId(),
                    adminUser.getEmail() + " stopped impersonating role" + getRolesDisplayString(_previousRoleNames), adminUser);
            AuditLogService.get().addEvent(adminUser, stopEvent);
        }

        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(context.getContainer().getId(),
                adminUser.getEmail() + " impersonated role" + getRolesDisplayString(_roleNames), adminUser);
        AuditLogService.get().addEvent(adminUser, event);
    }

    private String getRolesDisplayString(Set<String> roleNames)
    {
        Set<String> roleDisplayNames = new HashSet<>();
        for (String name : roleNames)
            roleDisplayNames.add(RoleManager.getRole(name).getName());

        if (roleDisplayNames.isEmpty())
            return "";

        StringBuilder builder = new StringBuilder();
        if (roleDisplayNames.size() > 1)
            builder.append("s");
        builder.append(": ");
        builder.append(StringUtils.collectionToCommaDelimitedString(roleDisplayNames));
        builder.append(".");
        return builder.toString();
    }

    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }


    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        restoreSessionAttributes(request.getSession(true));

        User adminUser = getAdminUser();
        Container project = null == _projectId ? ContainerManager.getRoot() : ContainerManager.getForId(_projectId);
        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(project.getId(),
                adminUser.getEmail() + " stopped impersonating role" + getRolesDisplayString(_roleNames), adminUser);
        AuditLogService.get().addEvent(adminUser, event);
    }

    static void addMenu(NavTree menu)
    {
        addMenu(menu, "Roles");
    }

    private static void addMenu(NavTree menu, String text)
    {
        NavTree newRoleMenu = new NavTree(text);
        newRoleMenu.setScript("LABKEY.Security.Impersonation.showImpersonateRole();");
        menu.addChild(newRoleMenu);
    }

    public static Collection<Role> getValidImpersonationRoles(Container c)
    {
        Collection<Role> validRoles = new LinkedList<>();
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);

        // Add the valid roles
        for (Role role : RoleManager.getAllRoles())
            if (role.isAssignable() && role.isApplicable(policy, c))
                validRoles.add(role);

        return validRoles;
    }

    private class RoleImpersonationContext extends AbstractImpersonationContext
    {
        /** Hold on to the role names and not the Roles themselves for serialization purposes. See issue #15660 */
        // TODO: Hold only Set<Role> and use custom serialization, see below
        private final Set<String> _roleNames;
        private transient Set<Role> _roles;

        private RoleImpersonationContext(@Nullable Container project, User user, Set<String> roleNames, ActionURL returnURL)
        {
            super(user, project, returnURL);
            verifyPermissions(project, user);
            _roleNames = roleNames;
        }

        // Throws if user is not authorized.
        private void verifyPermissions(@Nullable Container project, User user)
        {
            // Site/app admin can impersonate anywhere
            if (user.hasRootAdminPermission())
                return;

            // Must not be root
            if (null == project)
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate a role in the root", getFactory());

            // Must have admin permissions in project
            if (!project.hasPermission(user, AdminPermission.class))
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate a role in this project", getFactory());
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            return false;
        }

        @Override
        public String getCacheKey()
        {
            return _cacheKey;
        }

        @Override
        public int[] getGroups(User user)
        {
            return new int[0];
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return RoleImpersonationContextFactory.this;
        }

        // TODO: More expensive than it needs to be... should actually hold onto a Set<Roles> and use custom serialization (using unique names)
        private synchronized Set<Role> getRoles()
        {
            if (_roles == null)
            {
                _roles = new HashSet<>();
                for (String name : _roleNames)
                    _roles.add(RoleManager.getRole(name));
            }
            return _roles;
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return getFilteredContextualRoles(getRoles());
        }

        @Override
        public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
        {
            super.addMenu(menu, c, user, currentURL);
            RoleImpersonationContextFactory.addMenu(menu, "Adjust Impersonation");
        }
    }
}
