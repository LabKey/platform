package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.issues.IssuesSchema;

public class IssueCommentType implements AttachmentType
{
    private static final IssueCommentType INSTANCE = new IssueCommentType();

    public static IssueCommentType get()
    {
        return INSTANCE;
    }

    private IssueCommentType()
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
        return "SELECT EntityId AS ID FROM " + IssuesSchema.getInstance().getTableInfoComments().getSelectName();
    }
}
