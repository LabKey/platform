package org.labkey.announcements.model;

import org.labkey.announcements.AnnouncementModule;
import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 1/30/2015.
 */
public class TourAdministratorPermissions extends AbstractPermission
{
    public TourAdministratorPermissions()
    {
        super("Tour administration", "Allows user to create/edit/delete tours.", AnnouncementModule.class);
    }
}
