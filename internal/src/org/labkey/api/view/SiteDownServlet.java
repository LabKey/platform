/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Jul 12, 2006
 */
public class SiteDownServlet extends HttpServlet
{
    private String _message = "This LabKey Server is currently down for maintenance.";

    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _message = config.getInitParameter("message");
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        Writer writer = resp.getWriter();
        writer.write("<html><head><title>LabKey Server currently unavailable</title></head>\n");
        writer.write("<body>" + _message + "</body></html>");
    }
}
