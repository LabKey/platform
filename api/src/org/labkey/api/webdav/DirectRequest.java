/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
