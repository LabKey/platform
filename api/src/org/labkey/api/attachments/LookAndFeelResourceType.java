package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class LookAndFeelResourceType implements AttachmentType
{
    private static final LookAndFeelResourceType INSTANCE = new LookAndFeelResourceType();

    public static LookAndFeelResourceType get()
    {
        return INSTANCE;
    }

    private LookAndFeelResourceType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CoreSchema.getInstance().getTableInfoContainers(), "c").append(") AND (");
        sql.append(documentNameColumn).append(" IN (?, ?) OR ");
        sql.add(AttachmentCache.FAVICON_FILE_NAME);
        sql.add(AttachmentCache.STYLESHEET_FILE_NAME);
        sql.append(documentNameColumn).append(" LIKE '" + AttachmentCache.LOGO_FILE_NAME_PREFIX + "%' OR ");
        sql.append(documentNameColumn).append(" LIKE '" + AttachmentCache.MOBILE_LOGO_FILE_NAME_PREFIX + "%')");
    }
}
