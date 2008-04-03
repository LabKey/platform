package org.labkey.api.view;

import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jan 11, 2008
 */
public class SimpleTextDisplayElement extends DisplayElement
{
    private final String _text;
    private final boolean _html;

    public SimpleTextDisplayElement(String text, boolean isHtml)
    {
        _text = text;
        _html = isHtml;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (_html)
        {
            out.write(_text);
        }
        else
        {
            out.write(PageFlowUtil.filter(_text));
        }
    }
}
