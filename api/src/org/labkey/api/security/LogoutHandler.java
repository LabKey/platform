package org.labkey.api.security;

import javax.servlet.http.HttpSession;

public interface LogoutHandler
{
    public void handleLogout(User user, HttpSession session);
}
