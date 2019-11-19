package org.labkey.core.notification;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.AbstractContainerListener;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

public class EmailPreferenceContainerListener extends AbstractContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(CommSchema.getInstance().getTableInfoEmailPrefs(), c, null);
    }
}
