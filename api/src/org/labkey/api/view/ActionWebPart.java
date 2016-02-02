/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * User: matthewb
 * Date: Dec 20, 2007
 * Time: 3:02:54 PM
 */
public class ActionWebPart extends WebPartView
{
    ActionURL _url = null;

    public ActionWebPart(ActionURL url)
    {
        super(FrameType.DIV);
        _url = url;
        _url.setContextPath(Path.rootPath);
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        HttpServletRequest request = getViewContext().getRequest();
        HttpServletResponse response = getViewContext().getResponse();

        // catch and ignore close
        out.flush();
        final PrintWriter outWrapper = new PrintWriter(out)
        {
            public void close()
            {
            }
        };
        MockHttpServletResponse r = new MockHttpServletResponse()
        {
            @Override
            public PrintWriter getWriter()
            {
                return outWrapper;
            }

            public void setContentType(String s)
            {
                if (!s.startsWith("text/html"))
                {
                    throw new IllegalStateException("can only include html");
                }
            }
        };

        ViewServlet.forwardActionURL(request, r, _url);
        String redirect = (String)r.getHeader("Location");
        if (redirect != null)
        {
            out.write("<a href='");
            out.write(PageFlowUtil.filter(redirect));
            out.write("'>");
            out.write(PageFlowUtil.filter(redirect));
            out.write("</a>");
        }

        outWrapper.flush();
    }
}
