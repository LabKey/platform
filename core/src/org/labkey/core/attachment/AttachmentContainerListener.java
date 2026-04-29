package org.labkey.core.attachment;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

public class AttachmentContainerListener implements ContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(CoreSchema.getInstance().getTableInfoDocuments(), c, null);
        AttachmentCache.removeAttachments(c);
    }
}
