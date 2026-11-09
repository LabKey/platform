/*
 * Copyright (c) 2004-2026 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.writer.HtmlWriter;

/// A view that renders a fixed block of HTML content. Accepts either an [HtmlString] or a [DOM.Renderable].
///
/// ```java
/// // From safe, pre-encoded HTML
/// HtmlView view = new HtmlView(HtmlString.of("Hello, world!"));
///
/// // With a title (automatically uses PORTAL frame)
/// HtmlView view = new HtmlView("My Section", HtmlString.of("Some content"));
///
/// // From a DOM renderable
/// HtmlView view = new HtmlView(DOM.DIV("Hello"));
///
/// // Plain text (auto-escaped)
/// HtmlView view = HtmlView.of("User-supplied text");
///
/// // Error message styled with labkey-error
/// HtmlView view = HtmlView.err("Something went wrong");
/// ```
public class HtmlView extends WebPartView<Object>
{
    private String _contentType = null;
    private HtmlString _html;
    private DOM.Renderable _renderable;

    public HtmlView(DOM.Renderable renderable)
    {
        super(FrameType.DIV);
        _renderable = renderable;
    }

    public HtmlView(String title, DOM.Renderable renderable)
    {
        this(renderable);
        setTitle(title);
    }

    public HtmlView()
    {
        super(FrameType.DIV);
    }

    public HtmlView(HtmlString html)
    {
        super(FrameType.DIV);
        _html = html;
    }

    /** Use the HtmlString or Renderable constructor instead */
    @Deprecated
    public HtmlView(String html)
    {
        this(HtmlString.unsafe(html));
    }

    public static HtmlView of(String msg)
    {
        return new HtmlView(HtmlString.of(msg));
    }

    public static HtmlView err(String error) {
        return new HtmlView(
            HtmlStringBuilder.of(HtmlString.unsafe("<span class='labkey-error'>"))
                .append(error)
                .unsafeAppend("</span>")
        );
    }

    public static HtmlView unsafe(String html)
    {
        return new HtmlView(HtmlString.unsafe(html));
    }

    public HtmlView(String title, HtmlString html)
    {
        this(html);
        setTitle(title);
        setFrame(FrameType.PORTAL);
    }

    @Override
    public void setTitle(CharSequence title)
    {
        super.setTitle(title);
        if (StringUtils.isNotEmpty(title) && getFrame()==FrameType.DIV)
            setFrame(FrameType.PORTAL);
    }

    public void setHtml(HtmlString html)
    {
        _html = html;
    }

    public HtmlString getHtml()
    {
        return _html;
    }

    /**
     * if contentType is not null, we'd better not be in a template
     */
    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    @Override
    public void renderView(Object model, HtmlWriter out)
    {
        assert null == _contentType || getFrame() == FrameType.NONE || getFrame() == FrameType.NOT_HTML;

        if (null != _contentType)
            getViewContext().getResponse().setContentType(_contentType);

        if (_html != null)
            out.write(_html);
        else if (_renderable != null)
            _renderable.appendTo(out);
    }
}
