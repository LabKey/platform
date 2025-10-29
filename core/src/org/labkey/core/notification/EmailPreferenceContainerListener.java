package org.labkey.core.notification;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

public class EmailPreferenceContainerListener implements ContainerManager.ContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(CoreSchema.getInstance().getTableInfoEmailPrefs(), c, null);
    }
}
