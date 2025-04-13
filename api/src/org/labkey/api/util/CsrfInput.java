package org.labkey.api.util;

import jakarta.servlet.jsp.JspContext;
import org.labkey.api.view.ViewContext;

public class CsrfInput implements HasHtmlString, SafeToRender
{
    private final String _expectedToken;

    public CsrfInput(ViewContext context)
    {
        _expectedToken = CSRFUtil.getExpectedToken(context);
    }

    public CsrfInput(JspContext context)
    {
        _expectedToken = CSRFUtil.getExpectedToken(context);
    }

    @Override
    public HtmlString getHtmlString()
    {
        return InputBuilder.hidden().name(CSRFUtil.csrfName).value(_expectedToken).getHtmlString();
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }
}
