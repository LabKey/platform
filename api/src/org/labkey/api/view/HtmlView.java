/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;

/** Renders a fixed set of HTML at the content of the view */
public class HtmlView extends WebPartView
{
    private String _contentType = null;
    private Object[] _printfParams;
    private String _html = null;

    public HtmlView(String html)
    {
        super(FrameType.DIV);
        setHtml(html);
    }

    public HtmlView(String title, String html)
    {
        this(html);
        setTitle(title);
        setFrame(FrameType.PORTAL);
    }

    public HtmlView(String title, String html, Object... params)
    {
        this(title, html);
        _printfParams = params;
    }

    @Override
    public void setTitle(CharSequence title)
    {
        super.setTitle(title);
        if (StringUtils.isNotEmpty(title) && getFrame()==FrameType.DIV)
            setFrame(FrameType.PORTAL);
    }

    public void setHtml(String html)
    {
        _html = html;
    }

    public String getHtml()
    {
        return _html;
    }

    public void setPrintfParameters(Object... params)
    {
        _printfParams = params;
    }

    /**
     * if contentType is not null, we'd better not be in a template
     */
    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        assert null == _contentType || getFrame() == FrameType.NONE || getFrame() == FrameType.NOT_HTML;

        if (null != _contentType)
            getViewContext().getResponse().setContentType(_contentType);

        if (null != _printfParams)
            out.printf(_html, _printfParams);
        else
            out.print(_html);
    }
}
