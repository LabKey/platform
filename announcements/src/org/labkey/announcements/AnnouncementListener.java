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

import org.apache.log4j.Logger;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.MessageConfigManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;

import java.util.ArrayList;
import java.util.List;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:40:18 PM
 */
public class AnnouncementListener extends ContainerManager.AbstractContainerListener implements UserManager.UserListener, SecurityManager.GroupListener
{
    private static Logger _log = Logger.getLogger(AnnouncementListener.class);

    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    @Override
    public void containerDeleted(Container c, User user)
    {
        AnnouncementManager.purgeContainer(c);
    }


    @Override
    public void userAddedToSite(User user)
    {
    }

    @Override
    public void userDeletedFromSite(User user)
    {
        //when user is deleted from site, remove any corresponding record from EmailPrefs table.
        MessageConfigManager.deleteUserEmailPref(user, null);
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

    public void principalAddedToGroup(Group group, UserPrincipal user)
    {
    }

    @Override
    public void principalDeletedFromGroup(Group g, UserPrincipal p)
    {
        if (g.isProjectGroup() && p instanceof User)
        {
            User user = (User)p;
            Container cProject = ContainerManager.getForId(g.getContainer());
            List<User> memberList = SecurityManager.getProjectUsers(cProject, false);

            //if user is no longer a member of any project group, delete any EmailPrefs records
            if (!memberList.contains(user))
            {
                //find all containers for which this user could have an entry in EmailPrefs
                List<Container> containerList = new ArrayList<>(cProject.getChildren());
                //add project container to list
                containerList.add(cProject);
                MessageConfigManager.deleteUserEmailPref(user, containerList);
            }
        }
    }
}
