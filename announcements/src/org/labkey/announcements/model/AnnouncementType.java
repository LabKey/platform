package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;

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
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CommSchema.getInstance().getTableInfoAnnouncements(), "ann").append(")");
    }
}
