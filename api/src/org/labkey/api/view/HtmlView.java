/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;


public class HtmlView extends WebPartView
{
    private String contentType = null;
    private Object[] printfParams;

    public HtmlView(String html)
    {
        addObject("html", html);
    }


    public HtmlView(String title, String html)
    {
        setTitle(title);
        addObject("html", html);
    }

    public HtmlView(String title, String html, Object... params)
    {
        this(title, html);
        this.printfParams = params;
    }

    public void setPrintfParameters(Object... params)
    {
        printfParams = params;
    }

    /**
     * if contentType is not null, we'd better not be in a template
     */
    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        assert null == contentType || getFrame() == FrameType.NONE;
        if (null != contentType)
            getViewContext().getResponse().setContentType(contentType);

        if (null != printfParams)
            out.printf((String) getViewContext().get("html"), printfParams);
        else
            out.print(getViewContext().get("html"));
    }
}
