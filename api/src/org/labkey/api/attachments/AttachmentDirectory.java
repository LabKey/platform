package org.labkey.api.attachments;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 23, 2007
 * Time: 5:03:44 PM
 */
public interface AttachmentDirectory extends AttachmentParent
{
    public String getLabel();
    public File getFileSystemDirectory();
}
