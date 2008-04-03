package org.labkey.announcements.model;

import org.labkey.announcements.model.AnnouncementManager.EmailPref;
import org.labkey.announcements.model.Announcement;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: adam
 * Date: Mar 4, 2007
 * Time: 10:00:34 PM
 */
public class IndividualEmailPrefsSelector extends EmailPrefsSelector
{
    public IndividualEmailPrefsSelector(Container c) throws SQLException
    {
        super(c);
    }


    @Override
    protected boolean includeEmailPref(AnnouncementManager.EmailPref ep)
    {
        return super.includeEmailPref(ep) && ((ep.getEmailOptionId() & AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST) == 0);
    }


    public List<User> getNotificationUsers(Announcement ann) throws ServletException, SQLException
    {
        List<User> authorized = new ArrayList<User>(_emailPrefs.size());

        for (EmailPref ep : _emailPrefs)
            if (shouldSend(ann, ep))
                authorized.add(ep.getUser());

        return authorized;
    }
}
