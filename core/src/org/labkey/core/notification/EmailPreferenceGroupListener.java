package org.labkey.core.notification;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityManager.GroupListener;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

public class EmailPreferenceGroupListener implements GroupListener
{
    @Override
    public void principalAddedToGroup(Group group, UserPrincipal user)
    {
    }

    @Override
    // This seems unnecessary and wrong... users might still have permissions in this folder, directly or through site groups.
    // TODO: Remove this listener
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
                EmailPreferenceConfigManager.deleteUserEmailPref(user, containerList);
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
