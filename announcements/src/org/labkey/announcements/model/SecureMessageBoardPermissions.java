package org.labkey.announcements.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager.PermissionSet;
import org.labkey.api.security.User;

/**
 * User: adam
 * Date: Nov 16, 2006
 * Time: 9:56:01 AM
 */
public class SecureMessageBoardPermissions extends NormalMessageBoardPermissions
{
    protected final static int EDITOR_PERM = PermissionSet.EDITOR.getPermissions();

    public SecureMessageBoardPermissions(Container c, User user, AnnouncementManager.Settings settings)
    {
        super(c, user, settings);
    }

    public boolean allowRead(Announcement ann)
    {
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