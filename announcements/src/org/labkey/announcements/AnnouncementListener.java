/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.announcements.model.AnnouncementManager;
import org.apache.log4j.Logger;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:40:18 PM
 */
public class AnnouncementListener implements ContainerManager.ContainerListener, UserManager.UserListener, SecurityManager.GroupListener
{
    private static Logger _log = Logger.getLogger(AnnouncementListener.class);

    public void containerCreated(Container c)
    {
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    public void containerDeleted(Container c, User user)
    {
        try
        {
            AnnouncementManager.purgeContainer(c);
        }
        catch (Throwable t)
        {
            _log.error(t);
        }
    }


    public void userAddedToSite(User user)
    {
    }

    public void userDeletedFromSite(User user)
    {
        //when user is deleted from site, remove any corresponding record from EmailPrefs table.
        try
        {
            AnnouncementManager.deleteUserEmailPref(user, null);
            AnnouncementManager.deleteUserFromAllMemberLists(user);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }
    }

    public void userAccountDisabled(User user)
    {
        //TODO: what should go here?
    }

    public void userAccountEnabled(User user)
    {
        //TODO: what should go here?
    }

    public void principalAddedToGroup(Group group, UserPrincipal user)
    {
    }

    public void principalDeletedFromGroup(Group g, UserPrincipal p)
    {
        if (g.isProjectGroup() && p instanceof User)
        {
            User user = (User)p;
            Container cProject = ContainerManager.getForId(g.getContainer());
            List<User> memberList = org.labkey.api.security.SecurityManager.getProjectMembers(cProject, false);

            //if user is no longer a member of any project group, delete any EmailPrefs records
            if (!memberList.contains(user))
            {
                //find all containers for which this user could have an entry in EmailPrefs
                List<Container> containerList = new ArrayList<Container>(cProject.getChildren());
                //add project container to list
                containerList.add(cProject);
                try
                {

                    AnnouncementManager.deleteUserEmailPref(user, containerList);
                }
                catch (SQLException e)
                {
                    //is this the preferred way to handle any such errors?
                    _log.error(e);
                }
            }
        }
    }
}
