/*
 * Copyright (c) 2009-2009 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.ViewContext;
import org.labkey.api.search.SearchService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 10:33:38 AM
 */
public class ActionResource extends AbstractDocumentResource
{
    ActionURL _url;
    ActionURL _executeUrl;

    public ActionResource(String str)
    {
        super(new Path("action",str));
        _url = new ActionURL(str);
        _executeUrl = _url.clone();
        _executeUrl.replaceParameter("_print","1");
        _executeUrl.setScheme("http");
        _executeUrl.setHost("localhost");
    }


    public ActionResource(SearchService.SearchCategory category, ActionURL url)
    {
        this(category, url, url.clone());    
    }


    public ActionResource(SearchService.SearchCategory category, ActionURL url, ActionURL source)
    {
        this(url.getLocalURIString());
        _url = url;
        _executeUrl = source;
        _executeUrl.replaceParameter("_print","1");
        _executeUrl.setScheme("http");
        _executeUrl.setHost("localhost");
        _properties = new HashMap<String,Object>();
        _properties.put(SearchService.PROPERTY.category.toString(), category.getName());
    }
    

    public boolean exists()
    {
        return true;
    }                                                                                         


    @Override
    public String getContentType()
    {
        return "text/html";
    }

    
    public InputStream getInputStream(User user) throws IOException
    {
        return new ByteArrayInputStream(getResponse(user).getContentAsByteArray());
    }


    MockHttpServletResponse _response = null;
    
    MockHttpServletResponse getResponse(final User user) throws IOException
    {
        if (null == _response)
        {
            MockHttpServletRequest req = new MockHttpServletRequest(ViewServlet.getViewServletContext())
            {
                public Principal getUserPrincipal()
                {
                    return user;
                }

                @Override
                public String getMethod()
                {
                    return "GET";
                }
            };
            try
            {
                _response = ViewServlet.GET(req, _executeUrl, "text/html");
                return _response;
            }
            catch (IOException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }

        }
        return _response;
    }

    public String getExecuteHref(ViewContext context)
    {
        return getExecuteHref();
    }

    public String getExecuteHref()
    {
        return _url.getLocalURIString();
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new IllegalStateException();
    }


    public long getContentLength() throws IOException
    {
        throw new IllegalStateException();
    }
}
