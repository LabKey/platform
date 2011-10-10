package org.labkey.api.thumbnail;

import java.io.InputStream;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 7:01 AM
 */
public class Thumbnail
{
    private final InputStream _is;
    private final String _contentType;

    public Thumbnail(InputStream is, String contentType)
    {
        assert null != is : "InputStream should not be null!";
        _is = is;
        _contentType = contentType;
    }

    public InputStream getInputStream()
    {
        return _is;
    }

    public String getContentType()
    {
        return _contentType;
    }
}
