package org.labkey.filecontent;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

public class FileSystemAttachmentType implements AttachmentType
{
    private static final FileSystemAttachmentType INSTANCE = new FileSystemAttachmentType();

    public static FileSystemAttachmentType get()
    {
        return INSTANCE;
    }

    private FileSystemAttachmentType()
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
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(CoreSchema.getInstance().getMappedDirectories(), "md").append(")");
    }
}
