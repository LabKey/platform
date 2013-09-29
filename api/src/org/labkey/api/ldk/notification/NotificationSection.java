package org.labkey.api.ldk.notification;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 9/23/13
 * Time: 9:05 PM
 */
public interface NotificationSection
{
    public String getMessage(Container c, User u);

    public boolean isAvailable(Container c, User u);
}
