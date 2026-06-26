package org.labkey.announcements.model;

import java.util.Date;

public class AnnouncementFullModel extends AnnouncementModel
{
    private Date _approved = null;

    public Date getApproved()
    {
        return _approved;
    }

    public void setApproved(Date approved)
    {
        _approved = approved;
    }

    public boolean isSpam()
    {
        return AnnouncementManager.SPAM_MAGIC_DATE.equals(getApproved());
    }
}
