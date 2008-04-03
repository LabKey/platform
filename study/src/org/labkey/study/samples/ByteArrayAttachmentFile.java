package org.labkey.study.samples;


import org.labkey.api.attachments.AttachmentFile;

import java.io.*;

/**
 * User: brittp
 * Date: Apr 17, 2006
 * Time: 2:24:46 PM
 */
public class ByteArrayAttachmentFile implements AttachmentFile
{
    private String _contentType;
    private byte[] _content;
    private String _fileName;
    private InputStream _inputStream;

    public ByteArrayAttachmentFile(String fileName, byte[] content, String contentType)
    {
        _contentType = contentType;
        _content = content;
        _fileName = fileName;
    }

    public String getContentType()
    {
        return _contentType;
    }

    public byte[] getBytes()
    {
        return _content;
    }

    public String getFilename()
    {
        return _fileName;
    }

    public long getSize()
    {
        return _content.length;
    }

    public InputStream openInputStream()
    {
        if (_inputStream != null)
            throw new IllegalStateException("An unclosed input stream is already active for this ByteArrayAttachmentFile");
        _inputStream = new BufferedInputStream(new ByteArrayInputStream(_content));
        return _inputStream;
    }

    public void closeInputStream() throws IOException
    {
        if (_inputStream == null)
            throw new IllegalStateException("No input stream is active for this ByteArrayAttachmentFile");
        _inputStream.close();
    }

    public String getError()
    {
        return null;
    }

    public void setFilename(String filename)
    {
        _fileName = filename;
    }
}
