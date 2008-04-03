package org.labkey.announcements;

import org.labkey.api.jsp.JspBase;
import org.labkey.announcements.model.Announcement;
import org.labkey.announcements.model.AnnouncementManager;


abstract public class EmailNotificationPage extends JspBase
{
    public String threadURL;
    public String boardPath;
    public String boardURL;
    public String srcURL;
    public String siteURL;
    public String cssURL;
    public Announcement announcement;
    public String body;
    public AnnouncementManager.Settings settings;
    public String removeUrl;
    public Reason reason;
    public boolean includeGroups;

    public static enum Reason { broadcast, signedUp, memberList }
}
