package org.labkey.api.webdav;

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 24, 2008
 * Time: 8:23:36 PM
 */
public class HistoryImpl implements WebdavResolver.History
{
    int _userId;
    Date _date;
    String _message;
    String _href;

    public HistoryImpl(int userId, Date date, String message, String href)
    {
        _userId = userId;
        _date = date;
        _message = message;
        _href = href;
    }
    
    public User getUser()
    {
        return UserManager.getUser(_userId);
    }

    public Date getDate()
    {
        return _date;
    }

    public String getMessage()
    {
        return _message;
    }

    public String getHref()
    {
        return _href;
    }
}
