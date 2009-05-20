/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.core.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.core.user.UserController;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jul 16, 2008
 * Time: 4:11:34 PM
 */
public class CoreQuerySchema extends UserSchema
{
    private Set<Integer> _projectUserIds;

    public static final String USERS_TABLE_NAME = "Users";
    public static final String SITE_USERS_TABLE_NAME = "SiteUsers";
    public static final String PRINCIPALS_TABLE_NAME = "Principals";
    public static final String MEMBERS_TABLE_NAME = "Members";

    public CoreQuerySchema(User user, Container c)
    {
        super("core", user, c, CoreSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return PageFlowUtil.set(USERS_TABLE_NAME, SITE_USERS_TABLE_NAME, PRINCIPALS_TABLE_NAME, MEMBERS_TABLE_NAME);
    }


    public TableInfo createTable(String name)
    {
        if (USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getUsers();
        if (SITE_USERS_TABLE_NAME.equalsIgnoreCase(name))
            return getSiteUsers();
        if(PRINCIPALS_TABLE_NAME.equalsIgnoreCase(name))
            return getPrincipals();
        if(MEMBERS_TABLE_NAME.equalsIgnoreCase(name))
            return getMembers();
        return null;
    }

    public TableInfo getSiteUsers()
    {
        FilteredTable users = getUserTable();

        //only site admins are allowed to see all site users,
        //so if the user is not a site admin, add a filter that will
        //generate an empty set (CONSIDER: should we throw an exception here instead?)
        if(!getUser().isAdministrator())
            addNullSetFilter(users);
        users.setName("SiteUsers");

        return users;
    }

    public TableInfo getPrincipals()
    {
        TableInfo principalsBase = CoreSchema.getInstance().getTableInfoPrincipals();
        FilteredTable principals = new FilteredTable(principalsBase);

        //we expose userid, name and type via query
        ColumnInfo col = principals.wrapColumn(principalsBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setIsHidden(true);
        col.setReadOnly(true);
        col.setFk(new LookupForeignKey("UserId")
        {
            public TableInfo getLookupTableInfo()
            {
                return getMembers();
            }
        });
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Name"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Type"));
        col.setReadOnly(true);
        principals.addColumn(col);

        col = principals.wrapColumn(principalsBase.getColumn("Container"));
        col.setReadOnly(true);
        principals.addColumn(col);

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("UserId"));
        defCols.add(FieldKey.fromParts("Name"));
        defCols.add(FieldKey.fromParts("Type"));
        defCols.add(FieldKey.fromParts("Container"));
        principals.setDefaultVisibleColumns(defCols);

        //filter out inactive
        principals.addCondition(new SQLFragment("Active=?", true));

        //filter for container is null or container = current-container
        principals.addCondition(new SQLFragment("Container IS NULL or Container=?", getContainer().getProject()));

        //only users with admin perms in the container may see the principals
        if(!getContainer().hasPermission(getUser(), AdminPermission.class))
            addNullSetFilter(principals);

        return principals;
    }

    public TableInfo getMembers()
    {
        TableInfo membersBase = CoreSchema.getInstance().getTableInfoMembers();
        FilteredTable members = new FilteredTable(membersBase);

        ColumnInfo col = members.wrapColumn(membersBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setFk(new LookupForeignKey("UserId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return getPrincipals();
            }
        });
        members.addColumn(col);

        col = members.wrapColumn(membersBase.getColumn("GroupId"));
        col.setKeyField(true);
        col.setFk(new LookupForeignKey("UserId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return getPrincipals();
            }
        });
        members.addColumn(col);

        //if user doesn't have admin perms, add a null-set filter
        if(!getContainer().hasPermission(getUser(), AdminPermission.class))
            addNullSetFilter(members);
        else
        {
            Container container = getContainer();
            Container project = container.isRoot() ? container : container.getProject();

            //filter for groups defined in this container or null container
            members.addCondition(new SQLFragment("GroupId IN (SELECT UserId FROM " + CoreSchema.getInstance().getTableInfoPrincipals()
                    + " WHERE Container=? OR Container IS NULL)", project.getId()));
        }

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("UserId"));
        defCols.add(FieldKey.fromParts("GroupId"));
        members.setDefaultVisibleColumns(defCols);

        return members;
    }

    public TableInfo getUsers()
    {
        if (getContainer().isRoot())
            return getSiteUsers();

        FilteredTable users = getUserTable();

        //if the user is a guest, add a filter to produce a null set
        if(getUser().isGuest())
            addNullSetFilter(users);
        else
        {
            if (_projectUserIds == null)
            {
                Container project = getContainer().getProject();
                _projectUserIds = new HashSet<Integer>(org.labkey.api.security.SecurityManager.getProjectUserids(project));
                Group siteAdminGroup = org.labkey.api.security.SecurityManager.getGroup(Group.groupAdministrators);
                try
                {
                    for (User adminUser : org.labkey.api.security.SecurityManager.getGroupMembers(siteAdminGroup))
                    {
                        _projectUserIds.add(adminUser.getUserId());
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new UnexpectedException(e);
                }
            }
            ColumnInfo userid = users.getRealTable().getColumn("userid");
            users.addInClause(userid, _projectUserIds);
        }

        return users;
    }

    protected FilteredTable getUserTable()
    {
        TableInfo usersBase = CoreSchema.getInstance().getTableInfoUsers();
        FilteredTable users = new FilteredTable(usersBase);

        ColumnInfo userIdCol = users.addWrapColumn(usersBase.getColumn("UserId"));
        userIdCol.setKeyField(true);
        userIdCol.setIsHidden(true);
        userIdCol.setReadOnly(true);

        ColumnInfo entityIdCol = users.addWrapColumn(usersBase.getColumn("EntityId"));
        entityIdCol.setIsHidden(true);

        ColumnInfo displayNameCol = users.addWrapColumn(usersBase.getColumn("DisplayName"));
        displayNameCol.setReadOnly(true);
        users.addWrapColumn(usersBase.getColumn("FirstName"));
        users.addWrapColumn(usersBase.getColumn("LastName"));
        users.addWrapColumn(usersBase.getColumn("Description"));
        users.addWrapColumn(usersBase.getColumn("Created"));
        users.addWrapColumn(usersBase.getColumn("Modified"));
        
        if (getUser().isAdministrator() || getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            users.addWrapColumn(usersBase.getColumn("Email"));
            users.addWrapColumn(usersBase.getColumn("Phone"));
            users.addWrapColumn(usersBase.getColumn("Mobile"));
            users.addWrapColumn(usersBase.getColumn("Pager"));
            users.addWrapColumn(usersBase.getColumn("IM"));
            users.addWrapColumn(usersBase.getColumn("Active"));
            users.addWrapColumn(usersBase.getColumn("LastLogin"));

            // The details action requires admin permission so don't offer the link if they can't see it
            users.setDetailsURL(new DetailsURL(new ActionURL(UserController.DetailsAction.class, getContainer()), Collections.singletonMap("userId", "userId")));
        }


        List<FieldKey> defCols = new ArrayList<FieldKey>();
        for (ColumnInfo columnInfo : users.getColumns())
        {
            if (!columnInfo.isHidden() && !"Created".equalsIgnoreCase(columnInfo.getName()) && !"Modified".equalsIgnoreCase(columnInfo.getName()))
            {
                defCols.add(FieldKey.fromParts(columnInfo.getName()));
            }
        }
        users.setDefaultVisibleColumns(defCols);

        return users;
    }

    protected void addNullSetFilter(FilteredTable table)
    {
        table.addCondition(new SQLFragment("1=2"));
    }
}
