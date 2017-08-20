package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;
import org.labkey.study.StudySchema;

public class ProtocolDocumentType implements AttachmentType
{
    private static final ProtocolDocumentType INSTANCE = new ProtocolDocumentType();

    public static ProtocolDocumentType get()
    {
        return INSTANCE;
    }

    private ProtocolDocumentType()
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
        sql.append(parentColumn).append(" IN (SELECT ProtocolDocumentEntityId FROM ").append(StudySchema.getInstance().getTableInfoStudy(), "s").append(")");
    }
}
