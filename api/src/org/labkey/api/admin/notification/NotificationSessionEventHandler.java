package org.labkey.api.admin.notification;

import org.labkey.api.security.AuthNotify;
import org.labkey.api.security.SessionEventHandler;
import org.labkey.api.security.User;

import javax.servlet.http.HttpSession;

public class NotificationSessionEventHandler implements SessionEventHandler
{
    public void handleSessionDestroyed(User user, HttpSession session)
    {
        // notify websocket clients associated with this http session, the user has logged out
        NotificationService.get().closeServerEvents(user.getUserId(), session, AuthNotify.SessionLogOut);

        // notify any remaining websocket clients for this user that were not closed that the user has logged out elsewhere
        NotificationService.get().sendServerEvent(user.getUserId(), AuthNotify.LoggedOut);
    }
}
