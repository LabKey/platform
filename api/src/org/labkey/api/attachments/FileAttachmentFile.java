package org.labkey.api.attachments;

import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: Sep 11, 2007
 * Time: 9:23:50 AM
 */
public class FileAttachmentFile implements AttachmentFile
{
    private File _file;
    private InputStream _in;

    public FileAttachmentFile(File file)
    {
        _file = file;
    }

    public byte[] getBytes() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public String getContentType()
    {
        return PageFlowUtil.getContentTypeFor(_file.getName());
    }

    public String getError()
    {
        if (!_file.exists())
            return "Warning: " + getFilename() + " does not exist.";
        else
            return null;
    }

    public String getFilename()
    {
        return _file.getName();
    }

    public void setFilename(String filename)
    {
        throw new UnsupportedOperationException("Can't set filename");
    }

    public InputStream openInputStream() throws IOException
    {
        _in = new FileInputStream(_file);
        return _in;
    }

    public void closeInputStream() throws IOException
    {
        if (null != _in)
            _in.close();
    }

    public long getSize()
    {
        return _file.length();
    }
}
