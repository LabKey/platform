package org.labkey.api.security;

import javax.servlet.http.HttpSession;

/**
 * SessionEventHandlers allow modules to hook into session events such as sessionDestroyed in order to take action when
 * a session is destroyed. For example, you may want to close a WebSocket connection associated with a particular
 * session when the user logs out.
 */
public interface SessionEventHandler
{
    public void handleSessionDestroyed(User user, HttpSession session);
}
