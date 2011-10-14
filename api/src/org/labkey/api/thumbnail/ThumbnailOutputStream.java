package org.labkey.api.thumbnail;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * User: adam
 * Date: 10/13/11
 * Time: 4:08 PM
 */
public class ThumbnailOutputStream extends ByteArrayOutputStream
{
    public Thumbnail getThumbnail(String contentType)
    {
        InputStream is = new ByteArrayInputStream(toByteArray());
        return new Thumbnail(is, contentType);
    }
}
