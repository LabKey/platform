package org.labkey.api.ldk.notification;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;

import javax.mail.Address;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/19/12
 * Time: 3:19 PM
 */
abstract public class NotificationService
{
    static NotificationService _instance;

    public static NotificationService get()
    {
        return _instance;
    }

    static public void setInstance(NotificationService instance)
    {
        _instance = instance;
    }

    abstract public void registerNotification(Notification notification);

    abstract public Set<Notification> getNotifications(Container c, boolean includeAll);

    abstract public Notification getNotification(String key);

    abstract public boolean isActive(Notification n, Container c);

    abstract public long getLastRun(Notification n);

    abstract public User getUser(Notification n, Container c);

    abstract public Date getNextFireTime(Notification n);
}
