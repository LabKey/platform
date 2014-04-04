/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.study.requirements;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.Location;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: brittp
 * Date: Jun 7, 2007
 * Time: 4:29:29 PM
 */
public abstract class DefaultActor<A extends DefaultActor<A>> implements RequirementActor<A>
{
    // TODO: Unused... delete this?
    public void addMembers(User... users)
    {
        addMembers(null, users);
    }

    public void addMembers(@Nullable Location location, User... users)
    {
        Integer groupId = getGroupId(location, true);
        Group group = SecurityManager.getGroup(groupId);

        for (User user : users)
        {
            try
            {
                SecurityManager.addMember(group, user);
            }
            catch (InvalidGroupMembershipException e)
            {
                // Best effort, but log any exceptions. Exception is unlikely at this point, since
                // actors don't currently support groups as members.
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    public User[] getMembers()
    {
        return getMembers(null);
    }

    public User[] getMembers(@Nullable Location location)
    {
        Integer groupId = getGroupId(location, false);
        if (groupId == null)
            return new User[0];
        List<Pair<Integer, String>> userIds = SecurityManager.getGroupMemberNamesAndIds(groupId, true);     // include active and inactive
        User[] users = new User[userIds.size()];
        Iterator<Pair<Integer, String>> idIt = userIds.iterator();
        for (int i = 0; i < users.length; i++)
            users[i] = UserManager.getUser(idIt.next().getKey());
        return users;
    }

    public void removeMembers(User... members)
    {
        removeMembers(null, members);
    }

    public void removeMembers(@Nullable Location location, User... members)
    {
        Integer groupId = getGroupId(location, false);
        if (groupId == null)
            throw new IllegalStateException("Group Id does not exist.");
        Group group = SecurityManager.getGroup(groupId);
        if (group == null)
            throw new IllegalStateException("Group does not exist.");
        for (User member : members)
            SecurityManager.deleteMember(group, member);
    }

    public Group getGroup(@Nullable Location location)
    {
        Integer groupId = getGroupId(location, false);
        return SecurityManager.getGroup(groupId);
    }

    public Integer getGroupId(boolean createIfMissing)
    {
        return getGroupId(null, createIfMissing);
    }

    public Integer getGroupId(@Nullable Location location, boolean createIfMissing)
    {
        String groupName = getGroupName() + " " + getPrimaryKey();
        Integer groupId;
        if (location != null)
        {
            groupId = SecurityManager.getGroupId(getContainer(), groupName, location.getEntityId(), false);
            if (groupId == null && createIfMissing)
                groupId = SecurityManager.createGroup(getContainer(), groupName, PrincipalType.MODULE, location.getEntityId()).getUserId();
        }
        else
        {
            groupId = SecurityManager.getGroupId(getContainer(), groupName, false);
            if (groupId == null && createIfMissing)
                groupId = SecurityManager.createGroup(getContainer(), groupName, PrincipalType.MODULE).getUserId();
        }
        return groupId;
    }

    public void deleteAllGroups()
    {
        List<Integer> groupsToDelete = new ArrayList<>();
        List<? extends Location> locations = StudyManager.getInstance().getSites(getContainer());
        for (Location location : locations)
        {
            Integer id = getGroupId(location, false);
            if (id != null)
                groupsToDelete.add(id);
        }
        Integer id = getGroupId(false);
        if (id != null)
            groupsToDelete.add(id);

        for (Integer groupId : groupsToDelete)
        {
            Group group = SecurityManager.getGroup(groupId);
            if (group != null)
                SecurityManager.deleteGroup(group);
        }
    }

    public A create(User user)
    {
        if (getContainer() == null)
            throw new IllegalArgumentException("Container must be set for all requirements.");
        return Table.insert(user, getTableInfo(), (A) this);
    }

    public A update(User user)
    {
        return Table.update(user, getTableInfo(), (A) this, getPrimaryKey());
    }

    public void delete()
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            deleteAllGroups();
            Table.delete(getTableInfo(), getPrimaryKey());

            transaction.commit();
        }
    }

    protected abstract TableInfo getTableInfo();
}
