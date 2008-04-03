package org.labkey.api.data;

import org.apache.commons.lang.time.FastDateFormat;
import org.labkey.api.attachments.DocumentWriter;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * User: jeckels
 * Date: Nov 18, 2005
 */
public class CacheableWriter implements DocumentWriter {
    private String _contentType;
    private String _disposition;
    private int _size;
    private ByteArrayOutputStream _bOut = new ByteArrayOutputStream();
    private byte[] _bytes;

    public CacheableWriter()
    {
    }

    public CacheableWriter(String contentType, byte[] bytes)
    {
        _bytes = bytes;
        _size = _bytes.length;
        _contentType = contentType;
    }

    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    public void setContentDisposition(String value)
    {
        _disposition = value;
    }

    public void setContentLength(int size)
    {
        _size = size;
    }

    public OutputStream getOutputStream() throws IOException
    {
        return _bOut;
    }

    public void writeToResponse(HttpServletResponse response, Calendar expires) throws IOException
    {
        response.reset();
        response.setContentType(_contentType);
        if (expires != null)
        {
            String expiration = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT")).format(expires);
            response.setHeader("Expires", expiration);
        }
        if (_disposition != null)
        {
            response.setHeader("Content-Disposition", _disposition);
        }
        response.setContentLength(_size);
        if (_bytes == null)
        {
            _bytes = _bOut.toByteArray();
        }
        response.getOutputStream().write(_bytes);
    }
}
