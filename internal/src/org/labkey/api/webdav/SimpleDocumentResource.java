package org.labkey.api.webdav;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 25, 2009
 * Time: 5:31:23 PM
 */
public class SimpleDocumentResource extends AbstractDocumentResource
{
    String _documentId;
    String _contentType = "text/html";
    byte[] _body;
    ActionURL _executeUrl;

    public SimpleDocumentResource(Path path, String documentId, String contentType, byte[] body, ActionURL executeUrl, Map<String,Object> properties)
    {
        super(path);
        _documentId = documentId;
        _contentType = contentType;
        _body = body;
        _executeUrl = executeUrl;
        if (null != properties)
            _properties = new HashMap<String,Object>(properties);
    }

    public boolean exists()
    {
        return true;
    }

    public InputStream getInputStream(User user) throws IOException
    {
        return new ByteArrayInputStream(_body);
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public long getContentLength() throws IOException
    {
        return _body.length;
    }

    @Override
    public String getDocumentId()
    {
        return _documentId;
    }

    @Override
    public String getExecuteHref(ViewContext context)
    {
        return _executeUrl.getLocalURIString();
    }
}
