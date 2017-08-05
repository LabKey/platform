package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerParent;

public class LookAndFeelResourceAttachmentParent extends ContainerParent
{
    public LookAndFeelResourceAttachmentParent(Container c)
    {
        super(c);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return LookAndFeelResourceType.get();
    }
}
