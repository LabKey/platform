/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Context representing that a user is impersonating another user, and should not be treated as their normal self.
 * We stash simple properties (container and user id) in session and turn them into a context with objects on each request
 * User: adam
 * Date: 11/9/11
*/

public class UserImpersonationContextFactory extends AbstractImpersonationContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _adminUserId;
    private final int _impersonatedUserId;
    private final ActionURL _returnURL;

    public UserImpersonationContextFactory(@Nullable Container project, User adminUser, User impersonatedUser, ActionURL returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _impersonatedUserId = impersonatedUser.getUserId();
        _returnURL = returnURL;
    }


    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }


    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);

        return new UserImpersonationContext(project, getAdminUser(), UserManager.getUser(_impersonatedUserId), _returnURL);
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
        SecurityManager.setAuthenticatedUser(request, impersonatedUser, false);
        User adminUser = getAdminUser();

        UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(context.getContainer().getId(),
                adminUser.getEmail() + " impersonated " + impersonatedUser.getEmail(), adminUser);
        AuditLogService.get().addEvent(adminUser, event);

        UserManager.UserAuditEvent event2 = new UserManager.UserAuditEvent(context.getContainer().getId(),
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

            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(project.getId(),
                    impersonatedUser.getEmail() + " was no longer impersonated by " + adminUser.getEmail(), impersonatedUser);
            AuditLogService.get().addEvent(adminUser, event);

            UserManager.UserAuditEvent event2 = new UserManager.UserAuditEvent(project.getId(),
                    adminUser.getEmail() + " stopped impersonating " + impersonatedUser.getEmail(), adminUser);
            AuditLogService.get().addEvent(adminUser, event2);
        }
    }


    static void addMenu(NavTree menu)
    {
        NavTree userMenu = new NavTree("User");
        userMenu.setScript("LABKEY.Security.Impersonation.showImpersonateUser();");
        menu.addChild(userMenu);
    }

    // Somewhat redundant with verifyPermissions() below, but much more expensive in the single-user case. Keep these two methods in sync.
    // project == null means return all site users (if authorized)
    public static Collection<User> getValidImpersonationUsers(@Nullable Container project, User adminUser)
    {
        Collection<User> validUsers;

        // Site admin can impersonate any active user...
        if (null == project)
        {
            validUsers = adminUser.hasRootAdminPermission() ? UserManager.getActiveUsers(true) : Collections.emptyList();
        }
        else if (!project.hasPermission(adminUser, AdminPermission.class))
        {
            validUsers = Collections.emptyList();
        }
        else
        {
            validUsers = SecurityManager.getProjectUsers(project);
        }

        validUsers.remove(adminUser);

        return validUsers;
    }

    private class UserImpersonationContext extends AbstractImpersonationContext
    {
        private UserImpersonationContext(@Nullable Container project, User adminUser, User impersonatedUser, ActionURL returnURL)
        {
            super(adminUser, project, returnURL);
            verifyPermissions(project, impersonatedUser, adminUser);
        }

        // Keep in sync with getValidImpersonationUsers() above
        private void verifyPermissions(@Nullable Container project, User impersonatedUser, User adminUser)
        {
            if (impersonatedUser.equals(adminUser))
                throw new UnauthorizedImpersonationException("Can't impersonate yourself", getFactory());

            // Site/app admin can impersonate anywhere
            if (adminUser.hasRootAdminPermission())
                return;

            // Project admin...
            if (null != project && project.hasPermission(adminUser, AdminPermission.class) && SecurityManager.getProjectUsersIds(project).contains(impersonatedUser.getUserId()))
                return;

            throw new UnauthorizedImpersonationException("Can't impersonate this user", getFactory());
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            // Don't allow global roles (site admin, developer, etc.) if user is being impersonated within a project
            // or if the admin user is not a site admin
            return null == getImpersonationProject() && getAdminUser().isInSiteAdminGroup();
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
        public ImpersonationContextFactory getFactory()
        {
            return UserImpersonationContextFactory.this;
        }

        @Override
        public int[] getGroups(User user)
        {
            return GroupManager.getAllGroupsForPrincipal(user);
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return getFilteredContextualRoles(user.getStandardContextualRoles());
        }
    }
}
