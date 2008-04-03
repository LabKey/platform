package org.labkey.api.attachments;

import java.io.OutputStream;
import java.io.IOException;

/**
 * User: adam
* Date: Jan 4, 2007
* Time: 4:20:50 PM
*/
public interface DocumentWriter
{

    public void setContentType(String contentType);

    public void setContentDisposition(String value);

    public void setContentLength(int size);

    public OutputStream getOutputStream() throws IOException;

}
