package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;

public class SecureDocumentType implements AttachmentType
{
    private static final SecureDocumentType INSTANCE = new SecureDocumentType();

    public static SecureDocumentType get()
    {
        return INSTANCE;
    }

    private SecureDocumentType()
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
        sql.append("1 = 0");   // No secure documents in current deployments
    }
}