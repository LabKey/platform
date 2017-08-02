package org.labkey.wiki.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.AttachmentType;

public class WikiType implements AttachmentType
{
    private static final AttachmentType INSTANCE = new WikiType();

    private WikiType()
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
        return "SELECT EntityId AS ID FROM " + CommSchema.getInstance().getTableInfoPages().getSelectName();
    }
}
