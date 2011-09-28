package org.labkey.api.attachments;

import org.labkey.api.util.PageFlowUtil;
import org.systemsbiology.jrap.ByteAppender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 27, 2011
 * Time: 3:14:03 PM
 */
public class InputStreamAttachmentFile implements AttachmentFile
{
    private InputStream _is;
    private String _name;
    private ByteAppender _byteBuffer = new ByteAppender();

    public InputStreamAttachmentFile(InputStream is, String name)
    {
        _name = name;

        try {
            byte[] buffer = new byte[4096];
            int len;

            while ((len = is.read(buffer)) > 0)
                _byteBuffer.append(buffer, 0, len);

            _is = new ByteArrayInputStream(_byteBuffer.getBuffer());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public long getSize() throws IOException
    {
        return _byteBuffer.getCount();
    }

    @Override
    public String getError()
    {
        if (_is == null)
            return "Warning: " + getFilename() + " does not exist.";
        else
            return null;
    }

    @Override
    public String getFilename()
    {
        return _name;
    }

    @Override
    public void setFilename(String filename)
    {
        _name = filename;
    }

    @Override
    public String getContentType()
    {
        return PageFlowUtil.getContentTypeFor(getFilename());
    }

    @Override
    public byte[] getBytes() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        return _is;
    }

    @Override
    public void closeInputStream() throws IOException
    {
        if (null != _is)
            _is.close();
    }
}
