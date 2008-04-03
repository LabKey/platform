package org.labkey.announcements;

import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.Announcement;
import org.labkey.api.data.Container;
import org.labkey.api.jsp.JspBase;

import java.util.List;

abstract public class DailyDigestPage extends JspBase
{
    public Container c;
    public List<Announcement> announcements;
    public String conversationName;
    public String cssURL;
    public AnnouncementManager.Settings settings;
    public String boardUrl;
    public String boardPath;
    public String siteUrl;
    public String removeUrl;
    public boolean includeGroups;
}
