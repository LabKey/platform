package org.labkey.api.util.element;

import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.element.Input.InputBuilder;
import org.labkey.api.view.ViewContext;

import javax.servlet.jsp.JspContext;

public class CsrfInput implements HasHtmlString
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
        return new InputBuilder().type("hidden").name(CSRFUtil.csrfName).value(_expectedToken).getHtmlString();
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }
}
