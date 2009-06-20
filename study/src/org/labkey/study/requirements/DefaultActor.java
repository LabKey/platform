/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.study.model.StudyManager;
import org.labkey.study.StudySchema;
import org.labkey.api.util.Pair;
import org.labkey.api.study.Site;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jun 7, 2007
 * Time: 4:29:29 PM
 */
public abstract class DefaultActor<A extends DefaultActor<A>> implements RequirementActor<A>
{
    public void addMembers(User... users)
    {
        addMembers(null, users);
    }

    public void addMembers(Site site, User... users)
    {
        Integer groupId = getGroupId(site, true);
        Group group = org.labkey.api.security.SecurityManager.getGroup(groupId);
        for (User user : users)
            SecurityManager.addMember(group, user);
    }

    public User[] getMembers()
    {
        return getMembers(null);
    }

    public User[] getMembers(Site site)
    {
        Integer groupId = getGroupId(site, false);
        if (groupId == null)
            return new User[0];
        List<Pair<Integer, String>> userIds = SecurityManager.getGroupMemberNamesAndIds(groupId);
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

    public void removeMembers(Site site, User... members)
    {
        Integer groupId = getGroupId(site, false);
        if (groupId == null)
            throw new IllegalStateException("Group Id does not exist.");
        Group group = SecurityManager.getGroup(groupId);
        if (group == null)
            throw new IllegalStateException("Group does not exist.");
        for (User member : members)
            SecurityManager.deleteMember(group, member);
    }

    public Integer getGroupId(boolean createIfMissing)
    {
        return getGroupId(null, createIfMissing);
    }

    public Integer getGroupId(Site site, boolean createIfMissing)
    {
        String groupName = getGroupName() + " " + getPrimaryKey();
        Integer groupId;
        if (site != null)
        {
            groupId = SecurityManager.getGroupId(getContainer(), groupName, site.getEntityId(), false);
            if (groupId == null && createIfMissing)
                groupId = SecurityManager.createGroup(getContainer(), groupName, Group.typeModule, site.getEntityId()).getUserId();
        }
        else
        {
            groupId = SecurityManager.getGroupId(getContainer(), groupName, false);
            if (groupId == null && createIfMissing)
                groupId = SecurityManager.createGroup(getContainer(), groupName, Group.typeModule).getUserId();
        }
        return groupId;
    }

    public void deleteAllGroups()
    {
        List<Integer> groupsToDelete = new ArrayList<Integer>();
        Site[] sites = StudyManager.getInstance().getSites(getContainer());
        for (Site site : sites)
        {
            Integer id = getGroupId( site, false);
            if (id != null)
                groupsToDelete.add(id);
        }
        Integer id = getGroupId(false);
        if (id != null)
            groupsToDelete.add(id);

        for (Integer groupId : groupsToDelete)
        {
            Group group = org.labkey.api.security.SecurityManager.getGroup(groupId);
            if (group != null)
                SecurityManager.deleteGroup(group);
        }
    }

    public A create(User user)
    {
        if (getContainer() == null)
            throw new IllegalArgumentException("Container must be set for all requirements.");
        try
        {
            return Table.insert(user, getTableInfo(), (A) this);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public A update(User user)
    {
        try
        {
            return Table.update(user, getTableInfo(), (A) this, getPrimaryKey());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void delete()
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = false;
        try
        {
            if (!scope.isTransactionActive())
            {
                scope.beginTransaction();
                transactionOwner = true;
            }
            deleteAllGroups();
            Table.delete(getTableInfo(), getPrimaryKey());

            if (transactionOwner)
                scope.commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }

    protected abstract TableInfo getTableInfo();
}
