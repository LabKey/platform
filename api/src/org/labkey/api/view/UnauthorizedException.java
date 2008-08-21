/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class UnauthorizedException extends ServletException
{
    String _url = null;
    private Container _c = null;
    private boolean _requestBasicAuth;

    public UnauthorizedException(boolean requestBasicAuth)
    {
        this();
        setRequestBasicAuth(requestBasicAuth);
    }

    public UnauthorizedException()
    {
        this(null, null, null);
    }

    public UnauthorizedException(String message, String url, Container c)
    {
        super(StringUtils.defaultIfEmpty(message, "" + HttpServletResponse.SC_UNAUTHORIZED + ": User does not have permission to perform this operation"));
        _url = url;
        _c = c;
    }
    
    public UnauthorizedException(String message)
    {
        this(message, null, null);
    }

    public void setRequestBasicAuth(boolean requestBasicAuth)
    {
        _requestBasicAuth = requestBasicAuth;
    }

    public boolean isRequestBasicAuth()
    {
        return _requestBasicAuth;
    }

    public String getURL()
    {
        return _url;
    }

    public Container getContainer()
    {
        return _c;
    }
}