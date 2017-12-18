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
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupMembershipCache;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Used to indicate that a user is impersonating a specific group (site or project), and are not operating as their
 * normal logged-in self.
 *
 * User: adam
 * Date: 11/9/11
 * Time: 10:23 PM
 */
public class GroupImpersonationContextFactory extends AbstractImpersonationContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _groupId;
    private final ActionURL _returnURL;
    private final int _adminUserId;

    public GroupImpersonationContextFactory(@Nullable Container project, User adminUser, Group group, ActionURL returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _adminUserId = adminUser.getUserId();
        _groupId = group.getUserId();
        _returnURL = returnURL;
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);
        Group group = SecurityManager.getGroup(_groupId);

        return new GroupImpersonationContext(project, getAdminUser(), group, _returnURL);
    }


    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the group and force permissions check
        getImpersonationContext();

        // Stash (and remove) just the registered session attributes (e.g., permissions-related attributes)
        stashRegisteredSessionAttributes(context.getSession());

        Group group = SecurityManager.getGroup(_groupId);
        if (group != null)
        {
            User adminUser = getAdminUser();
            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(context.getContainer().getId(),
                    adminUser.getEmail() + " impersonated group: " + group.getName() + ".", adminUser);
            AuditLogService.get().addEvent(adminUser, event);
        }
    }

    @Override
    public void stopImpersonating(HttpServletRequest request)
    {
        restoreSessionAttributes(request.getSession(true));

        Group group = SecurityManager.getGroup(_groupId);
        if (group != null)
        {
            User adminUser = getAdminUser();
            Container project = null == _projectId ? ContainerManager.getRoot() : ContainerManager.getForId(_projectId);
            UserManager.UserAuditEvent event = new UserManager.UserAuditEvent(project.getId(),
                    adminUser.getEmail() + " stopped impersonating group: " + group.getName() + ".", adminUser);
            AuditLogService.get().addEvent(adminUser, event);
        }
    }

    @Override
    public User getAdminUser()
    {
        return UserManager.getUser(_adminUserId);
    }

    private static boolean canImpersonateGroup(@Nullable Container project, User user, Group group)
    {
        // Impersonating the "Site: Guests" group leads to confusion and is not useful. Better to just log out. See #20140.
        if (group.isGuests())
            return false;

        // Impersonating the "Site: Administrators" group as a non-site admin is confusing as well.
        if (group.isAdministrators() && !user.isInSiteAdminGroup())
            return false;

        // Site/app admin can impersonate any group
        if (user.hasRootAdminPermission())
            return true;

        // Project admin...
        if (null != project && project.hasPermission(user, AdminPermission.class))
        {
            // ...can impersonate any project group but must be a member of a site group to impersonate it
            if (group.isProjectGroup())
                return group.getContainer().equals(project.getId());
            else
                return user.isInGroup(group.getUserId());
        }

        return false;
    }

    static void addMenu(NavTree menu)
    {
        NavTree groupMenu = new NavTree("Group");
        groupMenu.setScript("LABKEY.Security.Impersonation.showImpersonateGroup();");
        menu.addChild(groupMenu);
    }

    public static Collection<Group> getValidImpersonationGroups(Container c, User user)
    {
        LinkedList<Group> validGroups = new LinkedList<>();
        List<Group> groups = SecurityManager.getGroups(c.getProject(), true);
        Container project = c.getProject();

        // Site groups are always first, followed by project groups
        for (Group group : groups)
            if (canImpersonateGroup(project, user, group))
                validGroups.add(group);

        return validGroups;
    }

    private class GroupImpersonationContext extends AbstractImpersonationContext
    {
        private final Group _group;
        private final int[] _groups;

        private GroupImpersonationContext(@Nullable Container project, User user, Group group, ActionURL returnURL)
        {
            // project is used to verify authorization, but isn't needed while impersonating... the group itself will
            // limit the admin user appropriately
            super(user, null, returnURL);

            if (!canImpersonateGroup(project, user, group))
                throw new UnauthorizedImpersonationException("You are not allowed to impersonate this group", getFactory());

            // Seed the group list with guests, site users, and the passed in group (as appropriate)
            LinkedList<Integer> seedGroups = new LinkedList<>();

            // Everyone always gets "Site: Guests" and "Site: Users"
            seedGroups.add(Group.groupGuests);
            seedGroups.add(Group.groupUsers);

            // All groups except "Site: Users" gets the requested group
            if (!group.isUsers())
                seedGroups.add(group.getUserId());

            // Now expand the list of groups to include all groups they belong to (see #13802)
            _groups = GroupMembershipCache.computeAllGroups(seedGroups);
            _group = group;
        }

        @Override
        public boolean isAllowedGlobalRoles()
        {
            return false;
        }

        @Override
        public String getCacheKey()
        {
            // NavTree for user impersonating a group will be different for each group
            return "/impersonationGroup=" + _group.getUserId();
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return GroupImpersonationContextFactory.this;
        }

        @Override
        public int[] getGroups(User user)
        {
            return _groups;
        }

        @Override
        public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
        {
            return getFilteredContextualRoles(user.getStandardContextualRoles());
        }
    }
}
