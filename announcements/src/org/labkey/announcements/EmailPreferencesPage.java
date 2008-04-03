package org.labkey.announcements;

import org.labkey.api.jsp.JspBase;

abstract public class EmailPreferencesPage extends JspBase
    {
    public String message;
    public int emailPreference;
    public String srcURL;
    public String conversationName;
    public boolean hasMemberList;
    public int notificationType;
    }
