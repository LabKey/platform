/*
 * Copyright (c) 2012-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.ldk.notification;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;

import javax.mail.Address;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
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

    //returns whether the service is enabled at the site level, which lets admins globally turn it off
    abstract public boolean isServiceEnabled();

    abstract public Address getReturnEmail(Container c);

    abstract public Set<UserPrincipal> getRecipients(Notification n, Container c);

    abstract public List<Address> getEmailsForPrincipal(UserPrincipal user) throws ValidEmail.InvalidEmailException;
}
