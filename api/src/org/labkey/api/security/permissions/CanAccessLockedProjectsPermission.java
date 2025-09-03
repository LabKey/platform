package org.labkey.api.security.permissions;

// Assigned to Site, Application, and Project admins so they can access locked projects. Also used as a contextual role
// to bypass the locked project check for specific scenarios (e.g., update user profile page).
public class CanAccessLockedProjectsPermission extends AbstractPermission
{
    public CanAccessLockedProjectsPermission()
    {
        super("Can Access Locked Projects", "Allows admins to access locked projects");
    }
}
