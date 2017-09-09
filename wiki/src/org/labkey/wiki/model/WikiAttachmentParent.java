package org.labkey.wiki.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.AttachmentParentEntity;

public class WikiAttachmentParent extends AttachmentParentEntity
{
    WikiAttachmentParent(Wiki wiki)
    {
        super(wiki);
    }

    @NotNull
    @Override
    public AttachmentType getAttachmentType()
    {
        return WikiType.get();
    }
}
