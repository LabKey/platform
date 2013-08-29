package org.labkey.api.webdav;

import com.google.common.collect.Multimap;

import java.net.URI;

/**
 * User: kevink
 * Date: 8/27/13
 *
 * Silly class that represents a request.  May just be able to use commons-httpclient HttpRequest instead.
 */
public class DirectRequest
{
    private String _method;
    private URI _endpoint;
    private Multimap<String, String> _headers;

    public DirectRequest(String method, URI endpoint, Multimap<String, String> headers)
    {
        _method = method;
        _endpoint = endpoint;
        _headers = headers;
    }

    public String getMethod()
    {
        return _method;
    }

    public URI getEndpoint()
    {
        return _endpoint;
    }

    public Multimap<String, String> getHeaders()
    {
        return _headers;
    }
}
