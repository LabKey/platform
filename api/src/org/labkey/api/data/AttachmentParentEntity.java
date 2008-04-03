package org.labkey.api.data;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;

import java.util.Collection;

/**
 * User: adam
 * Date: Mar 31, 2007
 * Time: 9:05:32 PM
 */
public class AttachmentParentEntity extends Entity implements AttachmentParent
{
    // Do nothing by default
    public void setAttachments(Collection<Attachment> attachments)
    {
    }
}
