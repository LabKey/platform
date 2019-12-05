/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.announcements;

import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager.UserListener;

import java.beans.PropertyChangeEvent;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:40:18 PM
 */
public class AnnouncementUserListener implements UserListener
{
    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    @Override
    public void userAddedToSite(User user)
    {
    }

    @Override
    public void userDeletedFromSite(User user)
    {
        AnnouncementManager.deleteUserFromAllMemberLists(user);
    }

    @Override
    public void userAccountDisabled(User user)
    {
        //TODO: what should go here?
    }

    @Override
    public void userAccountEnabled(User user)
    {
        //TODO: what should go here?
    }
}
