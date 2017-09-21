package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.EntityAttachmentParent;
import org.labkey.issue.model.Issue.Comment;

public class CommentAttachmentParent extends EntityAttachmentParent
{
    public CommentAttachmentParent(Comment comment)
    {
        super(comment);
    }

    public CommentAttachmentParent(String containerId, String entityId)
    {
        super(containerId, entityId);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return IssueCommentType.get();
    }
}
