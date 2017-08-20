package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.AttachmentParentEntity;

public class CommentAttachmentParent extends AttachmentParentEntity
{
    public CommentAttachmentParent(Issue.Comment comment)
    {
        this(comment.getContainerId(), comment.getEntityId());
    }

    public CommentAttachmentParent(String containerId, String entityId)
    {
        setContainer(containerId);
        setEntityId(entityId);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return IssueCommentType.get();
    }
}
