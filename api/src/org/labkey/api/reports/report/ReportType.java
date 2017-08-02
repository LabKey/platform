package org.labkey.api.reports.report;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.CoreSchema;

public class ReportType implements AttachmentType
{
    private static final ReportType INSTANCE = new ReportType();

    public static ReportType get()
    {
        return INSTANCE;
    }

    private ReportType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public @NotNull String getSelectSqlForIds()
    {
        return "SELECT EntityId AS ID FROM " + CoreSchema.getInstance().getTableInfoReport().getSelectName();
    }
}
