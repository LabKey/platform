package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.SQLFragment;
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
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        sql.append(parentColumn).append(" IN (SELECT EntityId FROM ").append(IssuesSchema.getInstance().getTableInfoComments(), "comments").append(")");
    }
}
