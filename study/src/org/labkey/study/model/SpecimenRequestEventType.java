package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;
import org.labkey.study.StudySchema;

public class SpecimenRequestEventType implements AttachmentType
{
    private static final SpecimenRequestEventType INSTANCE = new SpecimenRequestEventType();

    public static SpecimenRequestEventType get()
    {
        return INSTANCE;
    }

    private SpecimenRequestEventType()
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
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(StudySchema.getInstance().getTableInfoSampleRequestEvent(), "sre").append(")");
    }
}