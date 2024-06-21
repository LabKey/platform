/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.CanImpersonateSiteRolesPermission;
import org.labkey.api.security.roles.AbstractRootContainerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.RoleSet;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoleImpersonationContextFactory extends AbstractImpersonationContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final RoleSet _roles;
    private final RoleSet _previousRoles;
    @JsonIgnore // Can't be handled by remote pipelines
    private final ActionURL _returnURL;
    private final String _cacheKey;

    @JsonCreator
    protected RoleImpersonationContextFactory(
            @JsonProperty("_projectId") @Nullable GUID projectId,
            @JsonProperty("_adminUserId") int adminUserId,
            @JsonProperty("_roles") RoleSet roles,
            @JsonProperty("_previousRoles") RoleSet previousRoles,
            @JsonProperty("_cacheKey") String cacheKey
    )
    {
        _projectId = projectId;
        _adminUserId = adminUserId;
        _roles = roles;
        _previousRoles = previousRoles;
        _returnURL = null;
        _cacheKey = cacheKey;
    }
    
    public RoleImpersonationContextFactory(@Nullable Container project, User adminUser, Collection<Role> newImpersonationRoles, Set<Role> currentImpersonationRoles, ActionURL returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _returnURL = returnURL;

        // Compute the navtree cache key based on role names + project: NavTree will be different for each role set + project combination
        StringBuilder cacheKey = new StringBuilder("/impersonationRole=");

        for (Role role : newImpersonationRoles)
        {
            String roleName = role.getUniqueName();
            cacheKey.append(roleName);
            cacheKey.append("|");
        }

        _roles = new RoleSet(newImpersonationRoles);
        _previousRoles = new RoleSet(currentImpersonationRoles);

        if (null != _projectId)
            cacheKey.append("/impersonationProject=").append(_projectId);

        _cacheKey = cacheKey.toString();
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        return new RoleImpersonationContext(project, getAdminUser(), _roles, _returnURL, this, _cacheKey);
    }

    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the role(s) and force permissions check
        getImpersonationContext();

        // Stash (and remove) just the registered session attributes (e.g., permissions-related attributes)
        stashRegisteredSessionAttributes(context.getSession());

        User adminUser = getAdminUser();

        if (!_previousRoles.isEmpty())
        {
            UserManager.UserAuditEvent stopEvent = new UserManager.UserAuditEvent(context.getContainer().getId(),
                    adminUser.getEmail() + " stopped impersonating role" + getRolesDisplayString(_previousRoles), adminUser);
            AuditLogService.get().addEvent(adminUser, stopEvent);
        }

        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(context.getContainer().getId(),
                adminUser.getEmail() + " impersonated role" + getRolesDisplayString(_roles), adminUser);
        AuditLogService.get().addEvent(adminUser, event);
    }

    private String getRolesDisplayString(RoleSet roleSet)
    {
        Set<Role> roles = roleSet.getRoles();
        Set<String> roleDisplayNames = roles.stream()
            .filter(Objects::nonNull)
            .map(Role::getName)
            .collect(Collectors.toSet());

        if (roleDisplayNames.isEmpty())
            return "";

        StringBuilder builder = new StringBuilder();
        if (roleDisplayNames.size() > 1)
            builder.append("s");
        builder.append(": ");
        builder.append(String.join(", ", roleDisplayNames));
        builder.append(".");
        return builder.toString();
    }

    @Override
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
        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(project.getId(),adminUser.getEmail()
            + " stopped impersonating role" + getRolesDisplayString(_roles), adminUser);
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

    public static Stream<Role> getValidImpersonationRoles(Container c, User user)
    {
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);
        boolean canImpersonatePrivilegedRoles = user.hasRootPermission(CanImpersonatePrivilegedSiteRolesPermission.class);

        // Stream the valid roles
        return RoleManager.getAllRoles().stream()
            .filter(Role::isAssignable)
            .filter(role -> role.isApplicable(policy, c))
            .filter(role -> !role.isPrivileged() || canImpersonatePrivilegedRoles);
    }

    private static class RoleImpersonationContext extends AbstractImpersonationContext
    {
        private final RoleSet _roles;
        private final String _cacheKey;

        @JsonCreator
        private RoleImpersonationContext(
                @JsonProperty("_project") @Nullable Container project,
                @JsonProperty("_adminUser") User adminUser,
                @JsonProperty("_roles") RoleSet roles,
                @JsonProperty("_factory") ImpersonationContextFactory factory,
                @JsonProperty("_cacheKey") String cacheKey)
        {
            this(project, adminUser, roles, null, factory, cacheKey);
        }

        private RoleImpersonationContext(
                @Nullable Container project,
                User adminUser,
                RoleSet roles,
                ActionURL returnURL,
                ImpersonationContextFactory factory,
                String cacheKey)
        {
            super(adminUser, project, returnURL, factory);
            _roles = roles;
            _cacheKey = cacheKey;
            verifyPermissions(project, adminUser, _roles.getRoles());
        }

        // Throws if user is not authorized to impersonate all roles
        private void verifyPermissions(@Nullable Container project, User user, Set<Role> roles)
        {
            if (null == project)
            {
                // Ensure we have either site roles or project roles, not both
                var map = roles.stream()
                    .collect(Collectors.groupingBy(role -> role instanceof AbstractRootContainerRole));

                // UI prevents this, but crafty admin could attempt it by crafting a post with specific class names
                if (map.size() > 1)
                    throw new UnauthorizedImpersonationException("You are not allowed to impersonate site roles and project roles at the same time", getFactory());

                // Site Administrator and Impersonating Troubleshooter can impersonate any site role
                if (user.hasRootPermission(CanImpersonatePrivilegedSiteRolesPermission.class))
                    return;

                if (!user.hasRootPermission(CanImpersonateSiteRolesPermission.class))
                    throw new UnauthorizedImpersonationException("You are not allowed to impersonate site roles", getFactory());

                // Application Administrator can impersonate all site roles except the privileged ones
                List<String> privileged = roles.stream()
                    .filter(Role::isPrivileged)
                    .map(Role::getDisplayName)
                    .toList();

                if (!privileged.isEmpty())
                    throw new UnauthorizedImpersonationException("You are not allowed to impersonate " + StringUtilsLabKey.joinWithConjunction(privileged, "or"), getFactory());
            }
            else
            {
                // Must have admin permissions in this project
                if (!project.hasPermission(user, AdminPermission.class))
                    throw new UnauthorizedImpersonationException("You are not allowed to impersonate a role in this project", getFactory());

                // Must not be impersonating any site roles
                if (roles.stream().anyMatch(role -> (role instanceof AbstractRootContainerRole)))
                    throw new UnauthorizedImpersonationException("You are not allowed to impersonate site roles", getFactory());
            }
        }

        @Override
        public String getCacheKey()
        {
            return _cacheKey;
        }

        @Override
        public PrincipalArray getGroups(User user)
        {
            return PrincipalArray.getEmptyPrincipalArray();
        }

        @Override
        public Stream<Role> getSiteRoles(User user)
        {
            // Return only site roles that are being impersonated
            return super.getSiteRoles(user).filter(_roles::contains);
        }

        @Override
        public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
        {
            // No filtering - we trust verifyPermissions to validate that the admin is allowed to impersonate the
            // specified roles. See Issue #50248 to understand the Container check.
            return resource instanceof Container ? _roles.stream() : Stream.empty();
        }

        @Override
        public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
        {
            super.addMenu(menu, c, user, currentURL);
            RoleImpersonationContextFactory.addMenu(menu, "Adjust Impersonation");
        }
    }
}
