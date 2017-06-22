/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.announcements.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;

/**
 * User: adam
 * Date: Nov 16, 2006
 * Time: 9:54:48 AM
 */

public class NormalMessageBoardPermissions implements Permissions
{
    protected final Container _c;
    protected final User _user;
    protected final DiscussionService.Settings _settings;

    public NormalMessageBoardPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        _c = c;
        _user = user;
        _settings = settings;
    }

    public boolean allowRead(@Nullable AnnouncementModel ann)
    {
        return hasPermission(ReadPermission.class);
    }

    public boolean allowInsert()
    {
        return hasPermission(InsertPermission.class)|| hasPermission(InsertMessagePermission.class);
    }

    public boolean allowResponse(AnnouncementModel ann)
    {
        return allowInsert();
    }

    public boolean allowUpdate(AnnouncementModel ann)
    {
        return _c.hasPermission(_user, UpdatePermission.class,
                (ann.getCreatedBy() == _user.getUserId() && !_user.isGuest() ? RoleManager.roleSet(OwnerRole.class) : null));
    }

    public boolean allowDeleteMessage(AnnouncementModel ann)
    {
        //to delete, user must have delete permission for this message and all responses
        if (_c.hasPermission(_user, DeletePermission.class,
                (ann.getCreatedBy() == _user.getUserId() && !_user.isGuest() ? RoleManager.roleSet(OwnerRole.class) : null)))
        {
            for (AnnouncementModel a : ann.getResponses())
                if (!allowDeleteMessage(a))
                    return false;

            return true;
        }
        else
            return false;
    }

    public boolean allowDeleteAnyThread()
    {
        return hasPermission(DeletePermission.class);
    }

    public SimpleFilter getThreadFilter()
    {
        return new SimpleFilter();
    }

    protected boolean hasPermission(Class<? extends Permission> perm)
    {
        return _c.hasPermission(_user, perm);
    }

    public boolean includeGroups()
    {
        return _settings.includeGroups() && hasPermission(AdminPermission.class);
    }
}