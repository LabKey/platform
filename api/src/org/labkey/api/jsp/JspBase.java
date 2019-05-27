package org.labkey.api.jsp;

import org.labkey.api.util.HtmlString;

public abstract class JspBase extends AbstractJspBase
{
    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public HtmlString h(String str)
    {
        return HtmlString.of(str);
    }
}
