package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.AttachmentType;

public class AnnouncementType implements AttachmentType
{
    private static final AttachmentType INSTANCE = new AnnouncementType();

    private AnnouncementType()
    {
    }

    public static AttachmentType get()
    {
        return INSTANCE;
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public @NotNull String getSelectSqlForIds()
    {
        return "SELECT EntityId AS ID FROM " + CommSchema.getInstance().getTableInfoAnnouncements().getSelectName();
    }
}
