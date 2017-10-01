package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;

public class ExpRunAttachmentParent implements AttachmentParent
{
    private final ExpRunImpl _run;

    public ExpRunAttachmentParent(ExpRunImpl run)
    {
        _run = run;
    }

    @Override
    public String getEntityId()
    {
        return _run.getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return _run.getContainer().getId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return AttachmentType.UNKNOWN;
    }
}
