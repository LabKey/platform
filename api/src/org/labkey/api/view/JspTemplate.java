package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;

import java.io.StringWriter;

/**
 * User: adam
 * Date: Aug 10, 2010
 * Time: 3:26:54 PM
 */

// Executes a JSP and renders output to a string.  Useful for JSP templating of SQL queries, etc.
public class JspTemplate<ModelClass> extends JspView<ModelClass>
{
    public JspTemplate(String page)
    {
        this(page, null);
    }

    public JspTemplate(String page, @Nullable ModelClass model)
    {
        super(page, model);
        setFrame(WebPartView.FrameType.NONE);
    }

    public String render() throws Exception
    {
        StringWriter out = new StringWriter();
        include(this, out);
        return out.getBuffer().toString().trim();
    }
}
