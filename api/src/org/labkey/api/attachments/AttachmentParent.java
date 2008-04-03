package org.labkey.api.attachments;

import java.util.Collection;
import java.io.File;

/**
 * User: adam
 * Date: Jan 18, 2007
 * Time: 5:00:10 PM
 */
public interface AttachmentParent
{
    public String getEntityId();
    public String getContainerId();

    // AttachmentServiceImpl uses this to retrieve the attachments of many parents with a single query.  Implementation
    // is not necessary in most cases.
    public void setAttachments(Collection<Attachment> attachments);
}
