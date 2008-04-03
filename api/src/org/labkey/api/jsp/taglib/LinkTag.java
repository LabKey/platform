package org.labkey.api.jsp.taglib;

import org.labkey.api.jsp.taglib.SimpleTagBase;
import org.labkey.api.util.URLHelper;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class LinkTag extends SimpleTagBase
{
    String _href;
    String _text;

    public void setHref(String href)
    {
        _href = href;
    }

    public void setHref(URLHelper url)
    {
        _href = url.toString();
    }

    public void setText(String text)
    {
        _text = text;
    }

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        out.write("[<a href=\"");
        out.write(h(_href));
        out.write("\">");
        out.write(h(_text));
        out.write("</a>]");
    }
}
