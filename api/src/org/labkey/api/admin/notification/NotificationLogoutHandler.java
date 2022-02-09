package org.labkey.api.admin.notification;

import org.labkey.api.security.AuthNotify;
import org.labkey.api.security.LogoutHandler;
import org.labkey.api.security.User;

import javax.servlet.http.HttpSession;

public class NotificationLogoutHandler implements LogoutHandler
{
    public void handleLogout(User user, HttpSession session)
    {
        // notify websocket clients associated with this http session, the user has logged out
        NotificationService.get().closeServerEvents(user.getUserId(), session, AuthNotify.SessionLogOut);

        // notify any remaining websocket clients for this user that were not closed that the user has logged out elsewhere
        NotificationService.get().sendServerEvent(user.getUserId(), AuthNotify.LoggedOut);
    }
}
