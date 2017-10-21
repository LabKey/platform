package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;

public class ExpRunAttachmentType implements AttachmentType
{
    private static final ExpRunAttachmentType INSTANCE = new ExpRunAttachmentType();

    public static ExpRunAttachmentType get()
    {
        return INSTANCE;
    }

    private ExpRunAttachmentType()
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
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(ExperimentService.get().getTinfoExperimentRun(), "er").append(")");
    }
}