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
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.PermissionsContext;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ImpersonatePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Context representing that a user is impersonating another user, and should not be treated as their normal self.
 * We stash simple properties (container and user id) in session and turn them into a context with objects on each request
*/
public class UserImpersonationContextFactory extends AbstractImpersonationContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final int _impersonatedUserId;
    @JsonIgnore // Can't be handled by remote pipelines
    private final ActionURL _returnUrl;

    @JsonCreator
    protected UserImpersonationContextFactory(
            @JsonProperty("_projectId") @Nullable GUID projectId,
            @JsonProperty("_adminUserId") int adminUserId,
            @JsonProperty("_impersonatedUserId") int impersonatedUserId
    )
    {
        _projectId = projectId;
        _adminUserId = adminUserId;
        _impersonatedUserId = impersonatedUserId;
        _returnUrl = null;
    }

    public UserImpersonationContextFactory(@Nullable Container project, User adminUser, User impersonatedUser, ActionURL returnUrl)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _impersonatedUserId = impersonatedUser.getUserId();
        _returnUrl = returnUrl;
    }

    @Override
    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }

    @Override
    public PermissionsContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        return new UserImpersonationContext(project, getAdminUser(), UserManager.getUser(_impersonatedUserId), _returnUrl, this);
    }

    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will force permissions check
        getImpersonationContext();
        HttpServletRequest request = context.getRequest();

        // When impersonating a user we clear the session, so stash all the admin's session attributes.
        stashAllSessionAttributes(request.getSession(true));

        // setAuthenticatedUser clears the session; caller will add the factory (including the admin's session attributes)
        User impersonatedUser = UserManager.getUser(_impersonatedUserId);
        SecurityManager.setAuthenticatedUser(request, null, impersonatedUser, false);
        User adminUser = getAdminUser();

        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(context.getContainer(),
                adminUser.getEmail() + " impersonated " + impersonatedUser.getEmail(), adminUser);
        AuditLogService.get().addEvent(adminUser, event);

        UserManager.UserAuditEvent event2 = new UserManager.UserAuditEvent(context.getContainer(),
                impersonatedUser.getEmail() + " was impersonated by " + adminUser.getEmail(), impersonatedUser);
        AuditLogService.get().addEvent(adminUser, event2);
    }

    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        SessionHelper.clearSession(request, false);
        restoreSessionAttributes(request.getSession(true));

        User impersonatedUser = UserManager.getUser(_impersonatedUserId);

        if (null != impersonatedUser)
        {
            User adminUser = getAdminUser();
            Container project = null == _projectId ? ContainerManager.getRoot() : ContainerManager.getForId(_projectId);

            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(project,
                    impersonatedUser.getEmail() + " was no longer impersonated by " + adminUser.getEmail(), impersonatedUser);
            AuditLogService.get().addEvent(adminUser, event);

            UserManager.UserAuditEvent event2 = new UserManager.UserAuditEvent(project,
                    adminUser.getEmail() + " stopped impersonating " + impersonatedUser.getEmail(), adminUser);
            AuditLogService.get().addEvent(adminUser, event2);
        }
    }

    public static void addMenu(NavTree menu)
    {
        NavTree userMenu = new NavTree("User");
        userMenu.setScript("LABKEY.Security.Impersonation.showImpersonateUser();");
        menu.addChild(userMenu);
    }

    // Redundant with verifyPermissions() below, but much more expensive in the single-user case. Keep these two methods
    // in sync. project == null means return all site users (if authorized). Includes inactive users so we can show them
    // grayed out in the UI, but they can't be impersonated. Note that we allow app admins and project admins to
    // impersonate site admins and other users with privileged roles, but those roles are filtered out by
    // UserImpersonationContext. Project admins are also restricted to their project.
    public static Collection<User> getValidImpersonationUsers(@Nullable Container project, User adminUser)
    {
        // Must assign a mutable list in all scenarios to allow subsequent remove() call
        Collection<User> validUsers;

        // Site admin, app admin, and impersonating troubleshooter can impersonate any user, regardless of user's permissions and where impersonation is initiated
        if (adminUser.hasRootPermission(ImpersonatePermission.class))
        {
            validUsers = UserManager.getUsers(true);
        }
        else if (project != null && project.hasPermission(adminUser, ImpersonatePermission.class))
        {
            // The read permission check is not for security; it just seems useless to offer impersonation on a user who lacks read.
            validUsers = new ArrayList<>(SecurityManager.getUsersWithPermissions(project, false, Set.of(ReadPermission.class)));
        }
        else
        {
            validUsers = new ArrayList<>(0);
        }

        validUsers.remove(adminUser);

        return validUsers;
    }

    // Keep in sync with getValidImpersonationUsers() above
    private static void verifyPermissions(@Nullable Container project, User impersonatedUser, User adminUser, ImpersonationContextFactory factory)
    {
        if (impersonatedUser.equals(adminUser))
            throw new UnauthorizedImpersonationException("Can't impersonate yourself", factory);

        if (!impersonatedUser.isActive())
            throw new UnauthorizedImpersonationException("Can't impersonate an inactive user", factory);

        // Site admin, app admin, and impersonating troubleshooter can impersonate anywhere, regardless of user's permissions
        if (adminUser.hasRootPermission(ImpersonatePermission.class))
            return;

        // Project admin...
        if (project != null && project.hasPermission(adminUser, ImpersonatePermission.class) && project.hasPermission(impersonatedUser, ReadPermission.class))
            return;

        throw new UnauthorizedImpersonationException("Can't impersonate this user", factory);
    }

    private static class UserImpersonationContext extends AbstractImpersonationContext
    {
        @JsonCreator
        protected UserImpersonationContext(
            @JsonProperty("_project") @Nullable Container project,
            @JsonProperty("_adminUser") User adminUser,
            @JsonProperty("_factory") ImpersonationContextFactory factory
        )
        {
            super(adminUser, project, null, factory);
        }

        private UserImpersonationContext(@Nullable Container project, User adminUser, User impersonatedUser, ActionURL returnUrl, ImpersonationContextFactory factory)
        {
            super(adminUser, project, returnUrl, factory);
            verifyPermissions(project, impersonatedUser, adminUser, factory);
        }

        @Override
        public String getCacheKey()
        {
            // NavTree for user being impersonated will be different per impersonating user per project
            String suffix = "/impersonatingUser=" + getAdminUser().getUserId();

            if (null != getImpersonationProject())
                suffix += "/impersonationProject=" + getImpersonationProject().getId();

            return suffix;
        }

        @Override
        public PrincipalArray getGroups(User user)
        {
            return GroupManager.getAllGroupsForPrincipal(user);
        }

        @Override
        public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
        {
            return getFilteredRoles(super.getAssignedRoles(user, resource));
        }
    }
}
