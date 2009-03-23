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

import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.security.User;
import org.labkey.api.security.Group;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.data.*;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jul 16, 2008
 * Time: 4:11:34 PM
 */
public class CoreQuerySchema extends UserSchema
{
    private Set<Integer> _projectUserIds;

    public CoreQuerySchema(User user, Container c)
    {
        super("core", user, c, CoreSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return PageFlowUtil.set("Users","SiteUsers");
    }


    public TableInfo createTable(String name)
    {
        if (name.toLowerCase().equals("users"))
            return getUsers();
        if (name.toLowerCase().equals("siteusers"))
            return getSiteUsers();
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

        //we only expose user id and display name via Query
        ColumnInfo col = users.wrapColumn(usersBase.getColumn("UserId"));
        col.setKeyField(true);
        col.setIsHidden(true);
        col.setReadOnly(true);
        users.addColumn(col);

        col = users.wrapColumn(usersBase.getColumn("DisplayName"));
        col.setReadOnly(true);
        users.addColumn(col);

        List<FieldKey> defCols = new ArrayList<FieldKey>();
        defCols.add(FieldKey.fromParts("UserId"));
        defCols.add(FieldKey.fromParts("DisplayName"));
        users.setDefaultVisibleColumns(defCols);

        return users;
    }

    protected void addNullSetFilter(FilteredTable table)
    {
        table.addCondition(new SQLFragment("1=2"));
    }
}
