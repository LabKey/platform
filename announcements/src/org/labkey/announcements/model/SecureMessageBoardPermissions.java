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

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager.PermissionSet;
import org.labkey.api.security.User;
import org.labkey.api.announcements.DiscussionService;

/**
 * User: adam
 * Date: Nov 16, 2006
 * Time: 9:56:01 AM
 */
public class SecureMessageBoardPermissions extends NormalMessageBoardPermissions
{
    protected final static int EDITOR_PERM = PermissionSet.EDITOR.getPermissions();

    public SecureMessageBoardPermissions(Container c, User user, DiscussionService.Settings settings)
    {
        super(c, user, settings);
    }

    public boolean allowRead(Announcement ann)
    {
        if (_user == User.getSearchUser())
            return true;
        
        // Editors can read all messages
        if (hasPermission(EDITOR_PERM))
            return true;

        // If not an editor, message board must have a member list, user must be on it, and user must have read permissions
        return _settings.hasMemberList() && hasPermission(ACL.PERM_READ) && ann.getMemberList().contains(_user);
    }

    public boolean allowDeleteMessage(Announcement ann)
    {
        return false;
    }

    public boolean allowDeleteAnyThread()
    {
        return false;
    }

    public boolean allowResponse(Announcement ann)
    {
        // Editors can respond to any message
        if (hasPermission(EDITOR_PERM))
            return true;

        // If not an editor, message board must have a member list, user must be on it, and user must have insert permissions
        return _settings.hasMemberList() && hasPermission(ACL.PERM_INSERT) && ann.getMemberList().contains(_user);
    }

    public boolean allowUpdate(Announcement ann)
    {
        return false;
    }

    public SimpleFilter getThreadFilter()
    {
        SimpleFilter filter = super.getThreadFilter();

        if (!hasPermission(EDITOR_PERM))
            filter.addWhereClause("RowId IN (SELECT MessageId FROM " + _comm.getTableInfoMemberList() + " WHERE UserId = ?)", new Object[]{_user.getUserId()});

        return filter;
    }
}