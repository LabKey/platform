package org.labkey.api.attachments;

import java.io.InputStream;
import java.io.IOException;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 3:39:23 PM
 */
public interface AttachmentFile
{
    public long getSize();
    public String getError();
    public String getFilename();
    public void setFilename(String filename);
    public String getContentType();
    @Deprecated
    public byte[] getBytes() throws IOException;
    public InputStream openInputStream() throws IOException;
    public void closeInputStream() throws IOException;
}
