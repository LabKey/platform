package org.labkey.announcements;

import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

public class AnnouncementContainerListener extends ContainerManager.AbstractContainerListener
{
    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    @Override
    public void containerDeleted(Container c, User user)
    {
        AnnouncementManager.purgeContainer(c);
    }
}
