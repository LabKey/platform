package org.labkey.api.attachments;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.view.ViewContext;

/**
 * Created by xingyang on 12/8/15.
 */
public class AttachmentParentImpl implements AttachmentParent
{
    private final String _entityId;
    private final Container _c;

    public AttachmentParentImpl(String entityId, Container c)
    {
        _entityId = entityId;
        _c = c;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public String getContainerId()
    {
        return _c.getId();
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    @Override
    public SecurityPolicy getSecurityPolicy()
    {
        return null;
    }
}
