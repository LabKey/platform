/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

/**
 * User: adam
 * Date: Nov 16, 2006
 * Time: 9:54:48 AM
 */

public class NormalMessageBoardPermissions implements Permissions
{
    protected Container _c;
    protected User _user;
    protected DiscussionService.Settings _settings;
    protected static CommSchema _comm = CommSchema.getInstance();

    public NormalMessageBoardPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        _c = c;
        _user = user;
        _settings = settings;
    }

    public boolean allowRead(Announcement ann)
    {
        return hasPermission(ACL.PERM_READ);
    }

    public boolean allowInsert()
    {
        return hasPermission(ACL.PERM_INSERT);
    }

    public boolean allowResponse(Announcement ann)
    {
        return hasPermission(ACL.PERM_INSERT);
    }

    public boolean allowUpdate(Announcement ann)
    {
        // Either current user has update permissions on this container OR
        //   current user: is not a guest, has "update own" permissions, and created this message
        return hasPermission(ACL.PERM_UPDATE) ||
               (!_user.isGuest() && hasPermission(ACL.PERM_UPDATEOWN) && ann.getCreatedBy() == _user.getUserId());
    }

    public boolean allowDeleteMessage(Announcement ann)
    {
        // Simple case: current user has delete permissions on this container
        if (hasPermission(ACL.PERM_DELETE))
            return true;

        // Otherwise current user can't be a guest, must have "delete own" permissions, and must have created this message & all responses
        if (!_user.isGuest() && hasPermission(ACL.PERM_DELETEOWN) && ann.getCreatedBy() == _user.getUserId())
        {
            for (Announcement a : ann.getResponses())
                if (!allowDeleteMessage(a))
                    return false;

            return true;
        }

        return false;
    }

    public boolean allowDeleteAnyThread()
    {
        return hasPermission(ACL.PERM_DELETE);
    }

    public SimpleFilter getThreadFilter()
    {
        return new SimpleFilter();
    }

    protected boolean hasPermission(int perm)
    {
        return _c.hasPermission(_user, perm);
    }

    public boolean includeGroups()
    {
        return  _settings.includeGroups() && hasPermission(ACL.PERM_ADMIN);
    }
}