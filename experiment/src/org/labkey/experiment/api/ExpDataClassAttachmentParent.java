package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;

public class ExpDataClassAttachmentParent implements AttachmentParent
{
    private final Container _c;
    private final Lsid _lsid;

    public ExpDataClassAttachmentParent(Container c, Lsid lsid)
    {
        _c = c;
        _lsid = lsid;
    }

    @Override
    public String getEntityId()
    {
        return _lsid.getObjectId();
    }

    @Override
    public String getContainerId()
    {
        return _c.getId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return ExpDataClassType.get();
    }
}
