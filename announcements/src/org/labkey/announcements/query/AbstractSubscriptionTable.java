/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.announcements.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;

import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 10, 2012
 */
public class AbstractSubscriptionTable extends FilteredTable<AnnouncementSchema>
{
    public AbstractSubscriptionTable(TableInfo table, AnnouncementSchema schema)
    {
        super(table, schema);

        ColumnInfo userColumn = wrapColumn("User", getRealTable().getColumn("UserId"));
        addColumn(userColumn);
        userColumn.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), getContainer()));

        // Only admins can see subscriptions for other users
        if (!schema.getContainer().hasPermission(schema.getUser(), AdminPermission.class))
        {
            addCondition(getRealTable().getColumn("UserId"), schema.getUser().getUserId());
        }
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return hasPermission(user, perm, getContainer());
    }

    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm, Container container)
    {
        // Guests can't subscribe to anything, or edit anyone else's subscriptions, but they can read the table
        // It'll have no rows for them
        if (user.isGuest() && !ReadPermission.class.equals(perm))
        {
            return false;
        }

        // For authenticated users, if they have read access, that's enough to edit their own subscription level
        // The QueryUpdateService implementation will make sure they have permission to insert/update/delete at the row
        // level.
        return container.hasPermission(user, ReadPermission.class);
    }

    protected void ensurePermission(User user, User targetUser, Container targetContainer)
    {
        // First do the general check to make sure the user has permission to read this container at all
        if (!hasPermission(user, ReadPermission.class, targetContainer))
        {
            throw new UnauthorizedException();
        }

        // Then make sure the user is either an admin, or is looking for their own data
        if (!user.equals(targetUser) && !targetContainer.hasPermission(user, AdminPermission.class))
        {
            throw new UnauthorizedException();
        }
    }

    protected User getTargetUser(Map<String, Object> row, User user) throws InvalidKeyException
    {
        // Assume that if no User is specified, we should apply it to the current user
        User targetUser = user;
        Object userId = row.get("User");
        if (userId == null)
        {
            userId = row.get("_user");
        }
        if (userId != null)
        {
            try
            {
                targetUser = UserManager.getUser(Integer.parseInt(userId.toString()));
            }
            catch (NumberFormatException e)
            {
                throw new InvalidKeyException("Invalid User value: " + userId);
            }
        }
        return targetUser;
    }

}
