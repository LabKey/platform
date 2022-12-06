package org.labkey.api.action;

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.view.ViewContext;

public class LabKeyErrorWithLink extends LabKeyError
{
    private final String _adviceText;
    private final String _adviceHref;

    public LabKeyErrorWithLink(String message, String adviceText, String adviceHref)
    {
        super(message);
        _adviceText = adviceText;
        _adviceHref = adviceHref;
    }

    @Override
    public HtmlString renderToHTML(ViewContext context)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of(super.renderToHTML(context));
        builder.append(" ");
        builder.append(new LinkBuilder(getAdviceText()).href(getAdviceHref()).clearClasses());

        return builder.getHtmlString();
    }

    public String getAdviceText()
    {
        return _adviceText;
    }

    public String getAdviceHref()
    {
        return _adviceHref;
    }
}
