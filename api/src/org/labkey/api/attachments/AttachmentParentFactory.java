package org.labkey.api.attachments;

import org.labkey.api.data.Container;

/**
 * Delegate interface to generate an AttachmentParent
 */
public interface AttachmentParentFactory
{
    AttachmentParent generateAttachmentParent(String entityId, Container c);
}
