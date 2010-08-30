package org.labkey.api.announcements.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 5:50:52 PM
 */
public class AnnouncementService
{
    static private Interface instance;
    
    public static final String MODULE_NAME = "Announcement";
    
    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        // IRUD (Insert, Read, Update, Delete)
        Announcement insertAnnouncement(Container c, User u, String title, String body);

        // Get One
        Announcement getAnnouncement(Container container, int RowId);
        
        // Get Many
        List<Announcement> getAnnouncements(Container... containers);

        // Update
        Announcement updateAnnouncement(int RowId, Container c, User u, String title, String body);

        // Delete
        void deleteAnnouncement(Announcement announcement);
    }
}
