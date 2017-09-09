package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.AttachmentParentEntity;

public class AnnouncementAttachmentParent extends AttachmentParentEntity
{
    public AnnouncementAttachmentParent(AnnouncementModel ann)
    {
        super(ann);
    }

    @NotNull
    @Override
    public AttachmentType getAttachmentType()
    {
        return AnnouncementType.get();
    }
}
