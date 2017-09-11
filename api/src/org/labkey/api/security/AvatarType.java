package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class AvatarType implements AttachmentType
{
    private static final AvatarType INSTANCE = new AvatarType();

    public static AvatarType get()
    {
        return INSTANCE;
    }

    private AvatarType()
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
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CoreSchema.getInstance().getTableInfoUsers(), "users").append(")");
    }
}
