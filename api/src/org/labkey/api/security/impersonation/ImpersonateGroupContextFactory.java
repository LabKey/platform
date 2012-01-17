/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupMembershipCache;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewContext;

import java.util.LinkedList;

/**
 * User: adam
 * Date: 11/9/11
 * Time: 10:23 PM
 */
public class ImpersonateGroupContextFactory implements ImpersonationContextFactory
{
    private final @Nullable GUID _projectId;
    private final int _groupId;
    private final URLHelper _returnURL;
    private int _impersonatingUserId;

    public ImpersonateGroupContextFactory(@Nullable Container project, User impersonatingUser, Group group, URLHelper returnURL)
    {
        _projectId = null != project ? project.getEntityId() : null;
        _impersonatingUserId = impersonatingUser.getUserId();
        _groupId = group.getUserId();
        _returnURL = returnURL;
    }

    @Override
    public ImpersonationContext getImpersonationContext()
    {
        Container project = (null != _projectId ? ContainerManager.getForId(_projectId) : null);
        Group group = SecurityManager.getGroup(_groupId);
        User impersonatingUser = UserManager.getUser(_impersonatingUserId);

        return new ImpersonateGroupContext(project, impersonatingUser, group, _returnURL);
    }


    @Override
    public void startImpersonating(ViewContext context)
    {
        // Retrieving the context will get the groups and force permissions check
        getImpersonationContext();
        // TODO: Audit log?
    }

    @Override
    public void stopImpersonating(ViewContext context)
    {
        // TODO: Audit log?
    }

    public static boolean canImpersonateGroup(Container project, User user, Group group)
    {
        // Site admin can impersonate any group
        if (user.isAdministrator())
            return true;

        // Project admin...
        if (project.hasPermission(user, AdminPermission.class))
        {
            // ...can impersonate any project group but must be a member of a site group to impersonate it
            if (group.isProjectGroup())
                return group.getContainer().equals(project.getId());
            else
                return user.isInGroup(group.getUserId());
        }

        return false;
    }

    public class ImpersonateGroupContext implements ImpersonationContext
    {
        private final @Nullable Container _project;
        private final Group _group;
        private final int[] _groups;
        private final URLHelper _returnURL;

        private ImpersonateGroupContext(@Nullable Container project, User user, Group group, URLHelper returnURL)
        {
            if (!canImpersonateGroup(project, user, group))
                throw new IllegalStateException("You are not allowed to impersonate this group");

            // Seed the group list with guests, site users, and the passed in group (as appropriate)
            LinkedList<Integer> seedGroups = new LinkedList<Integer>();
            // Everyone always gets Guests
            seedGroups.add(Group.groupGuests);

            // Non-guest group gets site users
            if (!group.isGuests())
            {
                seedGroups.add(Group.groupUsers);

                // Non-site users group gets the requested group
                if (!group.isUsers())
                    seedGroups.add(group.getUserId());
            }

            // Now expand the list of groups to include all groups they belong to (see #13802)
            _groups = GroupMembershipCache.computeAllGroups(seedGroups);
            _project = project;
            _returnURL = returnURL;
            _group = group;
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
            // NavTree for user impersonating a group will be different for each group
            return "/impersonationGroup=" + _group.getUserId();
        }

        @Override
        public URLHelper getReturnURL()
        {
            return _returnURL;
        }

        @Override
        public ImpersonationContextFactory getFactory()
        {
            return ImpersonateGroupContextFactory.this;
        }

        @Override
        public int[] getGroups(User user)
        {
            return _groups;
        }
    }
}
