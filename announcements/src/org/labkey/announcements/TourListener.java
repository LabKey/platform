package org.labkey.announcements;

import org.labkey.announcements.model.TourManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

/**
 * Created by Marty on 2/25/2015.
 */
public class TourListener extends ContainerManager.AbstractContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        TourManager.purgeContainer(c);
    }
}
