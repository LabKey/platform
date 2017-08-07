package org.labkey.wiki.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;

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
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CommSchema.getInstance().getTableInfoPages(), "pages").append(")");
    }
}
